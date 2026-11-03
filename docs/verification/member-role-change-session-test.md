# DIFF-31 社員区分変更・認証世代検証（2026-10-18）

## 対象・実装

社員区分の変更後もセッションに古い講師権限が残る問題を修正した。develop、HEAD d63ebbaaで実施し、名称編集・コメント編集・検索条件維持・分類名重複等の既存未コミット変更は保持した。Excelは変更していない。最新の明示指示を基準とし、設計案・差分報告・認証／メンバー／スケジュール／Progress／Homeの実装と仕様を確認した。

`kenshu_member.auth_version`をログイン時のMemberPrincipalへ保持し、認証済みAPIは現在の社員区分・認証世代・業務ロールを照合する。不一致、社員不存在、世代を持たない旧形式セッションは401 AUTHENTICATION_REQUIREDとなり、当該端末のセッションを破棄する。他端末も次のAPIリクエストで拒否する。講師→新人→講師で世代は2増え、古い認証は復活しない。氏名のみの更新では世代は変わらない。認証世代はレスポンスDTOへ公開しない。

Spring Securityのセッション固定対策、パスワード消去、CSRF、既存の認可を維持した。標準のFACTOR_PASSWORD権限を許容し、期待する業務ロール以外は拒否する。DB照合失敗時は固定形式の500 INTERNAL_ERRORとし、古い権限で業務処理へ進まない。

## 更新競合への対策

認証済みの更新APIについて、PostgreSQLのトランザクションアドバイザリロック取得→世代再確認→既存の認可→Service更新→コミットを同一トランザクションで行う。更新レスポンスはコミット後に送る。失敗したトランザクションは成功扱いにしない。業務例外の400／403／409等は維持する。

小規模な構成に合わせ、同じDBを使用するアプリの更新API全体を1つのロックで直列化した。ロック待機は5秒、トランザクションは30秒。旧権限の更新が先にロックを取れば、そのコミット後に区分変更が確定する。区分変更が先なら、待機していた旧認証の更新は世代再確認で拒否する。複数サーバーでも同じPostgreSQL上のロックを使用する。直接SQLや、このフィルターを通さない将来のジョブまで自動保護するものではない。

## 社員区分変更の業務制約

次の場合は409 MEMBER_ROLE_CHANGE_NOT_ALLOWEDで全体を拒否する。

- 対象スケジュールの最後の編集可能講師を新人へ変更する。
- 対象スケジュールの最後の新人を講師へ変更する。
- 日別担当講師として参照されている社員を新人へ変更する。
- 対象新人の課題に未提出（区分1）以外の提出状況、またはコメントがある状態で講師へ変更する。

最後の条件はユーザー回答「未提出以外またはコメントあり」に基づく。未提出レコードだけなら、この履歴条件では禁止しない。コメントは投稿者を限定せず、対象新人の課題に紐づくものを対象とする。投稿者として別の課題に残したコメントだけを理由には禁止しない。

区分変更時は関連するschedule_can_edit.syain_kbnを同期する。JPA @Versionと既存監査処理により更新カウンタ・日時・更新者を更新し、画面IDはMMB01とする。can_edit、pin_flg、提出状況、コメントを自動変更・削除しない。新人のcan_edit=1も維持する。区分・世代・権限行は同一トランザクションで更新し、失敗時は全部をロールバックする。

自分自身を変更した場合も更新APIは200を返す。その後フロントがmeを再取得し、401でログイン画面へ誘導する。成功した保存を失敗メッセージに置き換えない。画面デザインは変更していない。

## Flyway V5・DB保護

V5__add_member_auth_version.sqlを新規追加した。auth_versionはBIGINT NOT NULL DEFAULT 0、0以上のCHECK、日本語コメント「認証世代番号」を持つ。既存V1〜V4、通常application.properties、spring.flyway.targetは変更していない。

既存の専用クラスタ127.0.0.1:59455/training_portal、test1だけを使用した。開発用5432のpublic/test1には接続・適用していない。既存の隔離領域を使用し、Docker構築は行っていない。Flyway12.4.0の隔離専用補助処理で接続先を検証し、target=5でV4・V5の2件を適用した。既存test1のV1〜V3履歴を修復・削除していない。再実行は追加0件、validate成功。

通常設定はtarget=2のままである。新コードはauth_version列を必須とするため、開発用publicを新コードで起動する前に、別途承認されたV5の適用手順が必要。今回、通常設定を勝手に変更したり、開発DBへV5を適用したりしていない。

## 自動テスト

最終バックエンド結果は190件成功、失敗0、エラー0、スキップ0。除外したIssueStatusMasterMigrationTestの2件はこの190件に含まない。途中の失敗を最終成功へ流用していない。

- バックエンド：隔離接続先と-Dspring.flyway.enabled=falseを指定し、./mvnw clean testを実行。IssueStatusMasterMigrationTestはmigrationを実行するため除外した。無条件の全件実行とは区別する。
- 実ログインの複数セッション失効、往復変更、新区分での再ログイン、氏名のみの変更、本人変更200→401、楽観ロック失敗時の世代不変を検証。
- 区分同期、編集権限・ピン・日別データの維持、最後の講師・新人・担当講師・提出履歴の拒否とデータ不変を検証。
- 独立した接続・コミットを使う並行テストで、旧更新と区分変更の両順序、2講師同時変更でも最後の編集可能講師が残ることを検証。この3件はINTEGRATION_TEST_PORT=59455を明示した場合に実行する。
- DB照合失敗時の業務チェーン未実行、世代を持たない認証の拒否を単体テスト。
- 既存のAPIテストは認証世代を持つテストPrincipalに移行した。削除済み社員の認証は従来の403ではなく401へ変更した。
- フロントエンド：211件成功、失敗0。本人区分変更後の再認証と、保存成功を失敗扱いしないケースを追加。
- TypeScript型チェック、lint、Webpack build成功。next/fontエラー再発なし。

## 検証途中の修正

最初のAPI回帰では、標準FACTOR_PASSWORDを想定しないチェックにより実ログイン後に401となった。標準認証要因と業務ロールを区別するよう修正した。型チェックで本人判定のselectedがnullになり得ることも検出し、明示的に存在を確認した。

実ブラウザの初回試験では区分変更200の後、失効401をブラウザが通信失敗と扱った。世代確認がCORS処理より先に実行され、失効レスポンスにCORSヘッダーがないことが原因だった。フィルターをCORSの後・CSRFと認可の前へ移し、失効401にも許可Originが返るAPI回帰を追加した。

## 実ブラウザ・最終監査

既存の隔離Chrome、実Next（59457）、実Spring Boot（59456）、専用PostgreSQL（59455）を使用した。合成講師E2EO自身の区分を新人へ変更し、PUT 200→GET me 401→ログイン画面を確認した。新しいログインは新人として成功し、メンバー管理への直接遷移も権限エラーとなった。初回のCORS失敗は記録を保持し、修正後に同じ操作を再実施して成功した。再試験準備のため、別の合成講師E2EIのメンバー管理画面からE2EOを一度講師へ戻した。合成データ以外は使用していない。

最終の合成社員E2EOは新人、auth_version=3（新人化→講師へ戻す→再度新人化）。同じ役割へ戻しても世代が戻らないことを実データでも確認した。権限行の社員区分不整合は0件。V5列はbigint、NOT NULL、default 0、0以上のCHECK。隔離publicのテーブル・シーケンスは0件、test1のV1〜V5はすべてsuccess=trueだった。

ログは${NON_DISTRIBUTED_EVIDENCE_DIR}/role-all.log、role-frontend.log、role-types.log、role-lint.log、role-webpack.log、role-audit.log。ブラウザのAPIパス・ステータス記録と合成画面は既存隔離一時ディレクトリ内のrole-browser-network.json、role-self-login-required.png、role-new-trainee.png。初回失敗のネットワーク記録も別名で保存した。資格情報・Cookie・HTTP本文はネットワーク証跡に記録していない。一時領域の長期保存は保証しない。

## 今回の変更ファイル

- src/main/resources/db/migration/V5__add_member_auth_version.sql（新規）
- src/main/java/com/training/portal/auth/MemberPrincipal.java、RoleSessionGuard.java、RoleSessionFilter.java（新規）
- src/main/java/com/training/portal/auth/MemberUserDetailsService.java、AuthResponse.java
- src/main/java/com/training/portal/config/SecurityConfig.java
- src/main/java/com/training/portal/member/KenshuMember.java、KenshuMemberService.java
- src/main/java/com/training/portal/member/MemberRoleChangeSupport.java、exception/MemberRoleChangeNotAllowedException.java（新規）
- src/main/java/com/training/portal/schedule/ScheduleCanEdit.java、ScheduleCanEditRepository.java
- src/main/java/com/training/portal/common/api/ApiExceptionHandler.java
- frontend/app/(app)/members/page.tsx、frontend/lib/auth-api.ts
- src/test/java/com/training/portal/auth/AuthApiTest.java、RoleSessionFilterTest.java（新規）、RoleSessionConcurrencyTest.java（新規）
- src/test/java/com/training/portal/member/MemberRoleChangeApiTest.java（新規）、KenshuMemberControllerTest.java、KenshuMemberServiceTest.java
- src/test/java/com/training/portal/support/SecurityTestUsers.java（新規）
- src/test/java/com/training/portal/home/HomeApiTest.java、progress/ProgressApiTest.java、schedule/ScheduleApiTest.java、integration/ScheduleProgressHomeIntegrationTest.java（既存の認証テストPrincipalを世代付きへ変更）
- frontend/tests/member-ui.test.tsx
- docs/api/auth.md、docs/api/members.md、本報告書

既存未コミットのProgress・スケジュール実装修正や他の検証報告は今回の変更一覧へ混在させていない。git diff --checkも成功。

## 残る制限・運用注意

- V5の開発DB適用は未承認・未実施。新コードを通常起動する前の必須作業。
- 全更新APIの直列化は小規模利用向け。負荷増加時は待機時間・スループットを評価し、ロック粒度を見直す必要がある。
- 全端末への即時画面通知は追加しない。次のリクエスト時に失効を検知する。既に認可済みの読み取りレスポンスは取り消さない。
- 複数サーバーの認証世代・更新ロックは同じDBで機能するが、HTTPセッション共有は追加していない。従来どおりルーティング等が必要。
- 外部SQLで社員区分だけを変更する運用は対象外。アプリの更新APIで世代と権限行も同時変更すること。
- 画面の権限制御とスケジュール個別権限は引き続き別の判定。新人の編集権限を削除する仕様は追加していない。

検証後は今回起動した専用Chrome・Next・Spring Bootを停止し、専用PostgreSQLも正常停止（exit=0）した。隔離データ・証跡・既存Docker・バックアップは削除していない。

commit/push/mergeは行っていない。

## 公開版の参照整理（2026-10-18、検証の再実行なし）

本文は当時の検証記録であり、今回の匿名化に伴う再試験結果ではない。成功件数、失敗した試行、スキップ、未実施の範囲は変更していない。現在の起動・構築は[新規環境セットアップ手順](../setup/fresh-environment.md)、最新の業務仕様は[API仕様書](../api/)を使用する。

`${NON_DISTRIBUTED_EVIDENCE_DIR}`は当時の非配布の一時証跡を示す匿名化表記であり、実行に必要な環境変数ではない。ログ・一時スクリプト・DBデータ領域・バックアップをこのリポジトリから取得できるという意味ではない。本文にあるログ名だけの参照も同様。パスを設定するだけで当時の検証を再現できるものではなく、一時証跡の長期保存も保証しない。

`${PRIVATE_BACKUP_DIR}`は管理者がGit管理外で保管するバックアップ領域の匿名化表記。バックアップ本体は非配布であり、他者がこのパスを指定して取得・復元できるものではない。SHA-256は当時の整合性検証の記録として保持した。公開用の構築手順は既存業務DBバックアップに依存しない。

再検証にはリポジトリ内の`tools/flyway/test-fresh-database.py`と関連テストを使用し、独立した検証PostgreSQLを準備する。新規構築検証の補助ツールは当時の全ブラウザ業務フローを再生するツールではない。本文で非配布の一時スクリプトだけにより実施したケースは、別途テスト手順を用意しなければそのまま自動再現できない。
