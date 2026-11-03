# DIFF-31：社員区分変更と全端末セッション失効の調査・設計案（2026-10-18）

## 1. 現在の実装

### 調査の前提

今回は調査・設計のみ。developの現在の作業ツリーを確認し、既存の未コミット変更を保持した。本報告書だけを追加し、アプリケーション・Excel・DB・Flyway・設定・依存関係は変更していない。DB接続、アプリ起動、テスト実行、Docker操作、commit/push/mergeも行っていない。以下の将来のファイル名・DB列・API応答は設計提案であり、実装済みではない。

確定事項：社員区分変更後に旧セッション権限を使わせず、対象社員に再ログインを要求する。既存スケジュール権限を自動削除しない。新人でもcan_edit=1を保持できる既存仕様は維持する。セッション数を1端末に制限する変更は含めない。

比較した主な資料：design-implementation-diff.md DIFF-31、auth/members/schedules/progress/homeのAPI仕様、基本詳細設計書のMMB_画面編集1 D12・共通仕様D6/D7/D8/D13、テーブル定義書の研修メンバー・スケジュール_編集権限。基本詳細側の「区分は新規/編集とも変更可」と、複数箇所のロール依存処理を照合した。

### 認証・セッション

- Spring Boot 4.1.1、ローカル依存のSpring Security 7.1.1を確認した。
- MemberUserDetailsService.loadUserByUsernameはkenshu_memberの現在区分を読み、1→ROLE_TRAINEE、2→ROLE_INSTRUCTORで標準UserDetailsを生成する。
- AuthController.loginはAuthenticationManagerでパスワード認証後、SessionAuthenticationStrategyを明示呼出しし、新しいSecurityContextを作成してSecurityContextRepository.saveContextで保存する。
- SecurityConfigはHttpSessionSecurityContextRepository、ChangeSessionIdAuthenticationStrategy、CsrfAuthenticationStrategyを使用する。認証時にセッションIDを変更し、CSRF情報を更新する。
- CookieはJSESSIONID、HttpOnly、通常のセッション期限は無操作1時間。logoutは当該リクエストのセッションを破棄し、認証情報をクリアしてCookie削除応答を返す。
- SessionRegistry、RegisterSessionAuthenticationStrategy、ConcurrentSessionFilter、HttpSessionEventPublisher、Spring Sessionの依存や共有SessionRepositoryは現コードに見当たらない。
- 現状は同じ社員が複数の独立したブラウザ/端末からログインできる。社員IDから全セッションを列挙する仕組みはない。同一ブラウザの通常の複数タブは同一Cookieを共有するため、独立セッションとは限らない。
- アプリケーションに共有・永続セッションの明示設定はない。通常のプロセス内HttpSessionとして扱われるが、コンテナ側のセッション復元設定まで実環境調査したわけではない。再起動をセッション失効の実装手段にしない。

### 社員区分変更とトランザクション

- PUT /api/members/{syainId}はSecurityConfigでROLE_INSTRUCTOR限定。ControllerはPrincipal名を操作社員として渡す。
- KenshuMemberService.updateは@Transactional。入力検証、既存updateCount照合、updateProfile、必要ならパスワード更新、flushを実行する。
- updateProfileはkenshu_member.syain_kbnを更新する。@Versionのupdate_countは一般的なプロフィール更新にも増分する。
- flushはSQL発行と競合の確定であり、トランザクションのCOMMITそのものではない。通常はServiceのトランザクションInterceptorを抜けた時点でコミットし、その後Controllerが応答を返す。
- 社員区分変更を検出する処理、他セッションの失効、schedule_can_editの区分同期、関連スケジュールの最低人数再検証はない。自己削除は禁止されるが、講師が自分の区分を新人へ変更すること自体を拒否する実装はない。

### 認可の参照元

| 対象 | 現在の参照元・判定 | 社員区分変更後の問題 |
| --- | --- | --- |
| メンバーCRUD | セッション内AuthenticationのROLE_INSTRUCTOR | 降格後も旧ロールで許可され得る |
| /api/auth/me | DBの現在syainKbnと、セッションのauthoritiesを組み合わせる | 新区分と旧ロールを同じ応答で返す可能性 |
| 上部メニュー・InstructorOnly | AuthProviderのauthorities | meを再取得しても旧ロールが残れば表示が一致しない |
| Home | DBの現在member区分、権限行の有無 | 画面の旧ロールと集計対象が食い違い得る |
| Progress | DBの現在member区分、課題権限、can_edit | 同一セッションで機能ごとに旧/新区分の扱いが異なる |
| スケジュール更新/削除 | schedule_can_edit.can_edit=1。社員区分で一律拒否しない | 新人の編集権限を失効処理と混同すると既存仕様を壊す |
| スケジュール詳細の権限表示 | ScheduleDetailResponse.Permissionはpermission.syainKbnを返す | コピーされた旧区分が残る |
| 新人の課題対象・Home集計 | findTraineePermissionsはmember.syainKbn=1で抽出 | 区分変更で対象集合が変わる。過去の提出行が消えたように見える可能性 |

根拠：AuthController、AuthResponse、MemberUserDetailsService、SecurityConfig、KenshuMemberService/KenshuMember、ScheduleCanEdit/ScheduleCanEditRepository、ScheduleDetailResponse、ScheduleService、HomeService、ProgressService、frontend/components/auth、frontend/lib/portal-store.ts、frontend/lib/auth-api.ts。

## 2. 問題点

1. DB区分更新は、既に各セッションへ保存されたAuthenticationのロールを更新/破棄しない。
2. meはロールの再認証ではなく、現在DBの区分と古い権限を混在させる可能性がある。フロントの表示だけを更新しても旧Cookieの直接APIは防げない。
3. 単一端末のlogoutでは他端末のセッションは失効しない。Cookie削除応答も他端末へ送れない。
4. 現在のsyainKbnとセッションロールの比較だけでは、講師→新人→講師と戻った場合、最初の講師セッションが再び一致する。再ログイン必須を維持できない（変更を往復するケース）。
5. 社員区分のコピー、日別担当講師、受講者の履歴、最低人数など、セッション以外の整合性もある。セッション失効だけでは解決しない。
6. COMMIT後のイベントで全セッションを列挙して失効させるだけでは、イベント処理失敗・プロセス停止・他サーバー・同時ログインに対する安全性をそれだけで保証できない。
7. Filterで確認済みの実行中リクエストは、途中でHttpSessionを失効させても、自動的に処理が中断・ロールバックするわけではない。保証する時点を明確にする必要がある。

## 3. 推奨設計

### 3.1 推奨の構成と保証範囲

既存のSpring Securityの認証・HttpSession・SecurityContextRepository・CSRF・logoutを維持する。その上に、DBに保存する単調増加の認証世代番号を安全判定の正として追加し、認証済みAPI要求ごとに照合する案を推奨する。

仮の列名はkenshu_member.auth_version。BIGINT NOT NULL DEFAULT 0、非負制約、社員区分変更時だけ+1とする。既存の楽観ロック用update_countとは分離する。値を公開DTOへ含めたりクライアントから受け取ったりしない。既存のSpring Security認証結果に社員ID・認証時の区分・auth_versionを保持する最小限のUserDetails実装を用意する。パスワードは認証後の既存資格情報消去を維持し、独自トークン/暗号方式は作らない。

これは業務ロジックとして必要な追加照合で、Spring Security標準のexpireNowだけで代替できるものではない。DB追加が未承認の場合は、後述の単一サーバー限定案へ制約を付けるか、既存update_count案の副作用を承認する必要がある。

保証：区分変更COMMIT後に開始してDB検証する要求は、全端末・全サーバーで旧世代を拒否する。古いセッションを新ロールへ自動昇格/降格して使い続けることはしない。本人のパスワード再認証が必要。無通信端末の画面を即時に書き換えるpushは含まないが、その端末の旧セッションも次の通信で利用できない。

厳密な物理セッション削除と、認証に使えない論理失効は区別する。DB世代番号は後者を保証する。全ノードから保存済みセッション自体を即時削除する要件が別にある場合は、Spring Sessionの共有ストアが必要になる。

### 3.2 ログインと要求時の処理

1. MemberUserDetailsServiceは、同じDB行のスナップショットから区分・世代・認証用ハッシュを取得する。別々の読取り結果を混ぜない。
2. 認証成功時の既存SessionAuthenticationStrategyと明示saveContextを維持する。認証結果の世代を、後から現在値で上書きしない。
3. ログインの認証中に区分変更がコミットした場合、古いスナップショットによるセッションは要求時の世代照合で拒否する。ログイン応答を組み立てる時も区分/世代が一致するか確認し、meやloginに新旧が混在する応答を作らない。
4. 認証済みAPI用のFilterを、SecurityContext読込み後・CSRFと認可前に1回だけ実行する。候補はSecurityContextHolderFilterの直後。実際のSecurityFilterChainをテストで検証する。Servlet Filterとして二重登録しない。
5. DBの社員が存在し、世代・区分と期待ロールが一致した場合のみ継続する。参照は書込み先と整合したDBを使用し、遅延レプリカや期限付きローカルキャッシュで旧世代を許可しない。
6. 世代欠落・不一致・社員不存在・不正ロールは認証継続不可。現在リクエストのSecurityContextを新しい空Contextへ置換し、保存済み認証を除去し、セッションをinvalidateする。必要なCookie削除を行い、401 AUTHENTICATION_REQUIREDを既存JSON形式で返す。共有ContextのAuthenticationを書き換えて他スレッドへ新権限を注入しない。
7. ログイン・未認証CSRF取得・logout・OPTIONS等について明示的な適用表を作る。新規ログインは旧認証へ依存せず再認証する。期限切れCSRFの自動再送やCSRF無効化はしない。logoutは既存の安全な破棄を維持する。
8. meも同じ検証を通し、旧世代に新しい権限を返して利用継続させない。

### 3.3 社員区分変更トランザクション

- 変更前後のsyainKbnを比較し、実際の区分変更だけを検出する。同じ区分で氏名を変えるだけならauth_versionは増やさない。
- 既存update_count検証を維持する。競合・入力不正・スケジュール整合性違反なら全体をロールバックする。
- 区分・auth_version増分・後述の権限行区分同期は同一トランザクションで確定する。失効用番号だけ別トランザクションで先に確定させない。
- コミット前に対象の全セッションを失効すると、更新ロールバックでもログアウトさせるので行わない。
- 対象社員・影響スケジュールを安定した順序でロックし、最低人数等を変更後の状態で検証する。flush成功だけで役割変更成功扱いしない。
- ロールバックでは世代も旧値のままなので既存セッションを使える。確定後は世代不一致で利用不可となる。
- DB外から区分だけを直接書き換えて世代を増やさない操作は禁止する。DB修正が必要な運用にも世代更新を含める。DBトリガーを勝手に追加する案ではない。

### 3.4 標準セッション失効機能の利用

現在の単一サーバーで、既存セッションを早期にexpiredへする補助機構にはSpring Security標準のSessionRegistryImpl、RegisterSessionAuthenticationStrategy、HttpSessionEventPublisher、ConcurrentSessionFilterを利用できる。実装する場合は既存の手動ログインにも登録戦略を明示的に組み込む。maximumSessions=1の制限は追加しない。

社員区分変更後のAFTER_COMMITイベントで当該社員の旧世代セッションをexpireNowする。ConcurrentSessionFilterでJSON用のSessionInformationExpiredStrategyを設定し、HTML遷移ではなく401へ揃える。expireNowは登録情報のexpired化であり、その呼出しだけで任意のHttpSessionがその場で破棄されるわけではない。

登録のキーは社員IDを安定して識別できるものとする。SessionRegistryはPrincipalをキーにするので、文字列社員IDをgetAllSessionsへ渡せば必ず列挙できるとは仮定しない。Principalのequals/hashCodeを安定化し、各SessionInformationまたは専用メタデータに認証時世代を保持する。イベントのコミット世代未満だけを失効させ、遅れて到着したイベントで新しい世代のログインを失効させない。状態変化しない世代一致のログインを巻き込まないテストが必要。

この補助機構は単一プロセスの一覧であり、他ノードを含む安全性の正にはしない。既にDB世代照合で全要求を拒否できるため、実装規模を抑える場合はこの補助機構を後回しにできる。全端末の利用拒否にSessionRegistry列挙成功を必須としない設計が推奨である。

### 3.5 実行中リクエスト・同時更新

要求開始時のFilter照合だけでは、照合直後に区分変更がコミットする競合を完全には防げない。確定仕様の厳密性を実装前に確認する。

安全側の推奨：書込みServiceの同一トランザクション内でも操作社員の認証世代を確認し、必要な読取りロックをコミットまで保持する。区分変更は同じ社員行の書込みロックと競合させる。これにより、旧権限で認可した書込みが先に完了するか、区分変更が先なら旧要求が拒否されるかを直列化する。要求のトークンに含まれた世代を検証し、DBの新世代をその要求の期待値にしてはいけない。

対象はメンバー・スケジュール・ピン・提出状況・コメント等の更新境界。ジョブやService直呼出しを許す場合は、別の明示的な認可入口が必要。FilterだけでServiceへの全経路を保護したとはしない。HTTP待機中の全時間に長いDBトランザクションを張る設計は避ける。

ロック順序は関連社員ID昇順→関連スケジュールID昇順等に統一し、自己変更では読取り→書込みの場当たり的な昇格を避けて必要なロックを最初から計画する。最低編集講師の同時降格や、スケジュール権限編集との競合も同じスケジュールの排他制御に参加させる。現在のScheduleServiceはこの共通の排他順を備えておらず、区分変更APIだけのロック追加で全体保証としない。実装前に具体的なロック集合・順序・タイムアウトを確定し、別接続による競合テストを行う。

既に処理中の参照レスポンスやブラウザ表示済みの情報は取り消せない。既存のリクエストを強制中断する仕様は提案しない。参照も変更後の応答を完全に禁止する必要があるかは確認事項とする。

### 3.6 自分自身の区分変更

- 現行は講師だけがメンバー更新できるため、本人変更で現実に起こり得る方向は講師→新人。新人の自己昇格APIは認可上許可しない。
- 変更が成立した現在のPUTは成功200を返す。DBコミット済みなのに「更新失敗」と誤認させる401へ差し替えない。
- 200後、フロントが自分のsyainKbn変更を検出して既存refreshUserを呼び、meが401となったらログインへ戻す。成功後に講師向け一覧再取得を続けない。更新後のロールを認証済み状態へ自動適用しない。
- この案ではMemberResponseのJSON項目追加は不要。必要ならログイン画面に「社員区分が変更されました。再ログインしてください。」を表示するが、既存401の固定文言との整合を確認してから追加する。
- ほかの端末も次の要求で401。無通信の画面を即時遷移させることは保証しない。外部端末のCookieそのものを変更APIの応答で消せるわけではない。
- 更新の応答が通信障害で失われた場合、勝手にPUTを再送しない。次の認証確認と再ログインで状態を再取得する。

## 4. 代替案とメリット・デメリット

| 案 | DB/追加構成 | メリット | 制約・判断 |
| --- | --- | --- | --- |
| A：SessionRegistryとexpireNowのみ | 業務DB変更なし、標準機能追加 | 単一サーバーでは比較的小さい実装 | イベント失敗・他ノード・ログイン競合への対応が必要。全端末確実拒否の唯一の根拠には弱い |
| B：毎要求で現在区分と旧ロールを比較 | DB変更なし、DB読取り追加 | 単純な降格は検出可能 | 区分が元に戻ると古いセッションが再利用可能。確定仕様を満たさないので非推奨 |
| C：既存update_countを認証世代として使う | DB変更なし、毎要求照合 | 単調な更新検出、複数ノードでも共通判定 | 氏名・ふりがな・パスワード等の変更でも全端末再ログインになる。追加の仕様として承認が必要 |
| D：専用auth_version＋毎要求照合（推奨） | kenshu_memberへ1列・新規migration、認証時スナップショット | 区分変更だけ失効、往復変更でも復活なし、イベント失敗・再起動・別ノードでも判定可能 | 1要求ごとのDB参照、DB障害時の利用停止、実行中書込みの直列化設計が必要 |
| E：Spring Session JDBC | 依存追加、SPRING_SESSION等の管理テーブル | 標準の共有HttpSession、社員ID索引から全セッションを列挙・削除可能 | 新しいセッション管理方式と運用・移行が必要。同時ログイン/進行中要求/削除失敗は世代照合等と組み合わせて対処 |
| F：Spring Session Redis | 依存追加、共有Redis | 複数サーバーで共有セッション、JDBC業務DBへのセッション保存を避ける | Redisの監視・障害対策・設定が必要。削除イベントだけを認可の安全性の正にしない |

現段階でRedisやセッション管理テーブルを新設する必然性はない。まずDで安全判定を成立させ、標準機能である既存HttpSessionを利用し続ける。DB追加なしを優先するならCの「全プロフィール更新で再ログイン」の承認を検討できる。Bだけは再ログイン要求を保証できない。

Spring Sessionを採用する場合は社員名索引が実際に登録されることを検証し、findByPrincipalNameで対象を取得してdeleteByIdする。手動ログインを使っているため、索引が自動で必ず埋まると未確認のまま実装しない。共有ストアの物理削除のみでも、同時処理による保存の競合や途中の認証済み要求を無視しない。

再起動：現行インメモリセッションは通常保持されない。永続化/復元を後日導入しても、旧世代はDB照合で拒否する。複数サーバー：同じ権限DBと世代を参照すれば各ノードの旧セッションを拒否できるが、現行ローカルHttpSessionのままでロードバランサー配下のセッション共有が実現するわけではない。stickinessまたはSpring Sessionの別途設計が必要。

## 5. 変更が必要なファイル（実装時の候補）

| ファイル/責務 | 変更案 |
| --- | --- |
| member/KenshuMember.java | auth_version保持・区分変更時増分。update_countは従来維持 |
| member/KenshuMemberRepository.java | 操作社員/変更対象の世代取得と必要なロック |
| auth/MemberUserDetailsService.java | 同一読取りから区分・認証世代を持つPrincipalを作成 |
| auth/MemberPrincipal.java（仮・新規） | 社員ID/区分/世代、資格情報消去、安定した同一社員識別 |
| auth/MemberAuthenticationFreshnessFilter.java（仮・新規） | 認可前の世代検証、401、失敗時拒否 |
| auth/MemberAuthenticationGuard.java（仮・新規） | Service更新トランザクションでの世代検証・ロックの共通化 |
| config/SecurityConfig.java | Filter順序、共通破棄・401、必要時だけ標準SessionRegistry関連を追加 |
| auth/AuthController.java、AuthResponse.java | login/meで新旧区分・ロール混在を防ぐ。自動権限更新をしない |
| member/KenshuMemberService.java | 実区分変更検出、全体検証、同一TXで世代と権限区分を更新 |
| member/exception、common/api/ApiExceptionHandler.java | 業務上変更不可エラーを追加する場合の固定形式。code/HTTPは要確定 |
| schedule/ScheduleCanEdit.java・Repository・ScheduleDetailResponse.java | can_edit/pinを保持した区分整合、バージョン更新・読取り元の統一 |
| ScheduleService、ProgressService等の更新境界 | 操作社員世代と最低人数/区分変更の競合に対する共通のロック順序 |
| frontend/app/(app)/members/page.tsx | 自分の区分変更成功後に認証再確認し、一覧の再取得を打ち切る |
| frontend/components/auth/auth-provider.tsx、lib/auth-api.ts | 現在の401クリアを再利用。必要な場合だけ再ログイン理由表示を追加 |
| 新規Flyway（例：次の未使用番号V5） | 推奨Dを承認した場合だけauth_versionを追加。V1～V4は変更禁止 |
| docs/api/auth.md・members.md・schedules.md | 再ログイン、区分変更拒否条件、カウンタ・認可の契約を記載 |
| AuthApiTest、Member Service/API/Persistence、Schedule/Progress/Home、frontend/tests | 次節のテストを追加 |

上表は実装許可後の候補一覧。今回作成したのは本報告書のみ。全ファイルを必ず変更するという確定計画ではない。

## 6. API・DB・既存スケジュールへの影響

### API契約

- PUT /api/members/{id}の既存入力・updateCountは維持。auth_versionは入力/公開レスポンスへ追加しない。
- 成立した区分変更は200。対象社員の以後の旧認証は401 AUTHENTICATION_REQUIREDで統一。正しく再ログインした上での権限不足は403。
- 関連スケジュールの整合性違反は全体拒否。提案は409 MEMBER_ROLE_CHANGE_NOT_ALLOWEDなどの業務エラーだが、既存のINVALID_INPUTを使うかも含めて実装前に確定する。未承認のcodeを追加済みと扱わない。
- 世代取得のDB障害は業務処理に進まず、既存の500 INTERNAL_ERROR等の安全な応答にする。認証不一致401と、判定不能なインフラ障害を区別する。自動再送はしない。

### schedule_can_editの区分

kenshu_member.syain_kbnを現在区分の正とし、schedule_can_edit.syain_kbnは現在区分を複製した列として同期する案を推奨する。ただし履歴区分として保持したいという業務要件がないことを確認する。現行は詳細DTOと並び順にコピー列、新人抽出にはmemberの現在区分を使用しており混在している。

同期する場合、当該社員の権限行だけを同一TXで更新し、can_edit・pin_flg・行の存在を保持する。区分が新人に変わってもcan_edit=1を残す。権限行を削除したり、参照のみに自動変更したりしない。区分同期には権限行のupdate_count・監査列も更新し、既に開いているスケジュール編集の古いカウンタが409となるようにする。JPAを迂回するbulk updateを使う場合もカウンタ更新と永続化Contextの整合を省略しない。

関連スケジュールの集約update_countまで増やすかは、現在の権限行単位の競合検証との整合を見て決定する。区分変更で複数スケジュールの集約カウンタを無条件更新する実装を先に入れない。

### 変更方向ごとの影響

| 方向 | スケジュール権限 | 追加で確認する整合性 |
| --- | --- | --- |
| 講師→新人 | can_edit=1のまま保持可能。ピンも保持 | 最後の編集可能講師を失わないか、日別担当講師として参照されていないか、新人分の仮想未提出が新たに増えることを許可するか |
| 新人→講師 | can_edit=0/1をそのまま保持 | 最後の新人を失わないか、提出状況・コメントを持つ受講者がProgress/Home集計から外れる扱いをどうするか |

現在のScheduleServiceは新人1人以上・編集可能講師1人以上を検証するが、MemberService経由の区分変更では検証しない。影響する全スケジュールについて変更後の人数を検証し、違反時は役割変更全体を拒否する案を推奨する。他人を自動で講師/編集者にする案ではない。

日別担当講師koshi_syain_idは現Serviceが講師区分を要求する。担当したまま新人に変えると既存スケジュールを編集できなくなる可能性があるため、先に担当変更するか、役割変更拒否とする案を確認する。レビュー者のkoshi_syain_idは社員区分に依存せずcan_edit=1が条件なので、日別担当と一律に扱わない。

新人→講師で既存提出状況のsinjin_syain_idやコメントは削除しない。現Home/Progressは現在新人だけを対象にするため、昇格により履歴が通常画面から除外される可能性がある。履歴表示を維持するための履歴ロールやアーカイブ機能を勝手に追加しない。実績がある場合は変更不可とするか、実績を保存したまま通常集計から外すことを承認するか、実装前に決める。

### 推奨DのDB変更

業務テーブルの追加は不要だが、auth_versionの1列追加は必要。既存全社員の初期値は0とし、旧Principalに世代がない場合は強制再ログインとする。移行を理由に旧セッションへ現在世代を自動補完して延命しない。BIGINTの増分失敗/上限は区分変更も失敗させる。

V1～V4は変更せず、次の未使用番号の新規migrationを設計する。Flyway targetの変更・public/test1への適用は別途承認と検証が必要で、今回行わない。Spring Session JDBC案を選ぶ場合は別途セッション管理表が増える。

## 7. セキュリティ上の注意点・失敗時の扱い

| 失敗・競合 | 安全側の処理 |
| --- | --- |
| 区分変更トランザクション失敗 | 区分・世代・権限コピー同期をすべてロールバックし、既存セッションを失効させない |
| AFTER_COMMITの列挙失効失敗 | DBの世代は既に進んでいるため旧認証は要求時に拒否。後処理失敗を記録し、DB変更失敗という応答や自動復元を行わない |
| 失効後処理前にプロセス停止 | 永続世代が安全判定の正なので旧認証は再利用不可。イベント再実行を唯一の根拠にしない |
| 毎要求のDB照合失敗 | 古い権限で続行しない。チェーンを止め固定インフラエラー。キャッシュ値へフォールバックしない |
| session.invalidate失敗 | 現要求は必ず拒否、現在Contextも破棄。以後も世代不一致で拒否する。Cookieの削除だけを安全性の根拠にしない |
| 同時の区分変更 | update_countと行ロックで直列化し、古い要求は409。往復変更でも世代は戻さない |
| 同時ログイン | 認証時スナップショットを検証。変更前の認証結果を変更後の世代で保存しない |
| 遅延した失効イベント | イベントの世代より古いセッションのみ対象。後から作った新しい認証を巻き込まない |
| 混在バージョンのサーバー | 世代照合しない旧サーバーがあれば保証不能。全ノード切替/停止を含む展開計画が必要 |

社員IDや失敗段階など必要な監査情報だけ記録し、パスワード・ハッシュ・Cookie・セッションID・CSRFトークンをログへ出さない。無操作端末への即時画面遷移や、既に表示済み情報の回収は別要件。名前変更だけで全端末をログアウトさせる機能は推奨Dでは追加しない。

## 8. 必要なテストケース（未実施の設計）

| No | ケース | 期待結果 |
| --- | --- | --- |
| 1 | 同一社員を独立した2ブラウザ相当セッションでログイン | 2件とも有効、同時ログイン数を勝手に制限しない |
| 2 | 別講師が対象講師を新人へ変更 | コミット後の旧セッションは全件401、membersを含む全APIで旧講師権限を使えない |
| 3 | 新人→講師へ変更 | 旧新人セッションは401。再認証後だけ新しい講師権限で動作 |
| 4 | 講師→新人→講師へ戻す | 最初の講師セッションは復活しない |
| 5 | 無通信端末・別ノードに旧セッションが残る | 次要求でDB世代不一致を検出し拒否 |
| 6 | 自分を講師→新人に変更 | 成立時200、その後me=401、ログインへ移動、成功後に講師一覧を再取得しない |
| 7 | 新人が自分を講師へ直接PUT | 403、DB/世代不変 |
| 8 | 氏名等だけ更新/同一区分を指定 | 推奨Dでは世代不変、セッション維持 |
| 9 | 入力不正・update_count競合 | 400/409、区分・世代・権限行が不変、他セッションも有効 |
| 10 | コミット直前に例外 | 区分・世代・権限同期すべてロールバック、失効イベントによる副作用なし |
| 11 | AFTER_COMMIT例外/Registry停止 | DBは成功、旧セッションは依然拒否、固定監査情報のみ |
| 12 | 世代照合のDB障害 | 業務処理なし、旧権限へのフォールバックなし |
| 13 | セッション世代欠落・破損/社員不存在 | 認証継続不可、旧セッションを新世代で自動補完しない |
| 14 | me/loginの区分変更競合 | 新区分＋旧authoritiesの混在応答なし |
| 15 | ログイン中の区分変更 | 古い認証結果は拒否、遅延イベントで正しい新ログインを巻き込まない |
| 16 | 区分変更と認可済み書込みの競合 | 合意した境界どおり直列化。区分変更確定後に旧世代の書込みを確定させない |
| 17 | 最後の編集可能講師を降格 | 推奨制約では拒否、全関連行とセッション世代不変 |
| 18 | 2講師の同時降格・同時権限変更 | どちらも検証をすり抜けて編集講師0人にならない |
| 19 | 最後の新人を昇格 | 合意した最低人数制約で全体拒否 |
| 20 | 日別担当講師・実績のある新人の区分変更 | 実装前に確定した拒否/保持方針どおり。提出状況・コメントを削除しない |
| 21 | 新人化した社員がcan_edit=1を保持 | 再ログイン後のスケジュール編集・名称編集が可能。ロールだけで拒否しない |
| 22 | 権限行の区分同期 | can_edit/pin/行数を維持、必要カウンタ・監査列だけ更新。古い編集要求は409 |
| 23 | Home・Progress・スケジュール・メンバー画面 | 再認証後の表示・候補・集計・ロールとDBが整合 |
| 24 | 新セッションのCSRF/固定攻撃対策/logout | ID更新・CSRF再取得を維持、古いCookie/CSRFでは更新できない |
| 25 | 再起動・復元・2ノード・旧ノード混在 | 世代照合の有無と保証範囲を実構成で確認 |
| 26 | セッション期限切れ・logout・登録イベント破棄 | Registryの不要エントリを掃除、Session ID変更後も失効漏れなし |

Service単体・実ログインを通すMockMvc・隔離DBの統合・フロント401/自己変更・実ブラウザで分担する。認可の@WithMockUserだけでは世代付き実セッションを検証できないので、GET csrf→POST login→実セッションを使う試験を必須にする。コミット後イベントはテスト全体がロールバックされる@Transactionalだけで確認せず、明示コミットする専用隔離試験で検証する。並行性は別接続・ラッチを用い、単一スレッド内の古いカウンタ試験だけで代替しない。

既存認証・Member・Schedule・Home・Progress・フロントエンドの回帰も実行する。開発DB public/test1は使用せず、既存の隔離環境を活用する。今回テストを実行したという意味ではない。

## 9. 実装前に確認が必要な仕様・次の作業

1. 推奨Dの認証世代1列と新規migrationを許可するか。DB追加なしを優先するならCの全プロフィール変更時ログアウトを許可するか。
2. 通常は1サーバーか、複数サーバー/共有セッションを今回の保証対象に含むか。論理的に旧認証を使えないことに加え、全ノードの保存セッションの即時物理削除も必須か。
3. 「変更後は旧権限不可」の境界を確定する。開始済み書込みも直列化する推奨案を採るか。開始済み参照レスポンスまで制約するか。
4. 影響スケジュールの最低編集講師1人・新人1人を失う区分変更は拒否するか。全システムの最後の講師の自己降格についても、初期管理者復旧を含め別途可否を決める。ここは既存スケジュールの最低人数とは別条件。
5. 日別担当講師として使用中の降格を拒否するか。提出状況/コメントを持つ新人の昇格で、履歴は保持して通常集計から除くのか、変更自体を拒否するのか。
6. schedule_can_edit.syain_kbnは現在区分のコピーとして同期してよいか。can_edit/pin/権限行は維持する。集約カウンタも増やすか。
7. 自己変更成功200→認証再確認→ログイン遷移のUXと固定メッセージ、整合性違反のHTTP/codeを確定する。

次は上記回答後に、トランザクション/ロック設計と対象範囲を確定し、隔離テストで現問題を再現してから実装する。今回の設計案を無承認でDBへ反映しない。

### Spring標準機能の確認資料

Spring Security 7.1.1の公式資料とローカル実装を照合した。手動認証のContext保存・セッション戦略・標準失効機能、Spring Sessionの共有ストアAPIを確認した。認証世代や業務上の整合性・ロック案は本アプリ向けの設計提案であり、Springが自動で提供する機能として記載していない。

- Authentication Persistence and Session Management：https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html
- SessionInformation（expireNow）：https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/core/session/SessionInformation.html
- ConcurrentSessionFilter：https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/web/session/ConcurrentSessionFilter.html
- Spring Session API：https://docs.spring.io/spring-session/reference/api.html
- FindByIndexNameSessionRepository：https://docs.spring.io/spring-session/docs/current/api/org/springframework/session/FindByIndexNameSessionRepository.html
