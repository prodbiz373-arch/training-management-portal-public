# Excel旧画面画像の差し替え結果（2026-10-18）

## 1. 結果と判定

**旧画面JPEG41枠を全て、新しく撮影した合成データの実画面へ差し替えた。未差し替え0件、旧41画像のバイト列残存0件。** 現在の業務状態に対応する40枠と、廃止済み自己登録誘導に対する現行ログインへの代替1枠に分ける。

ただし、41枠の全てが旧イベント札の瞬間を完全再現したという意味ではない。ログアウト・メンバー削除・コメント削除のネイティブ確認ダイアログ画像は未撮影で、実操作後の画像を使用した。分類・内容欄は対象領域を中心にした補完素材へ更新した。説明候補・表示/印刷の確認を残すため、**設計書としての完全合格・リポジトリのPublic化可能とは判定しない。** 個人情報未確認の旧画像を残して成功扱いにはしていない。

変更したリポジトリ内ファイルは基本詳細設計書と本報告書だけ。テーブル定義書、業務コード、認証仕様、SQL V1〜V5、通常設定、既存の検証資料は変更していない。Git履歴・インデックス・GitHub設定も変更せず、git add/commit/push/mergeなし。

## 2. 撮影準備・環境

- 新規の専用PostgreSQL17クラスタを作成。既存データのdump/復元や、開発DBのデータを使用していない。
- DB：127.0.0.1:51829/training_portal、public。開発用5432とは別データ領域・ソケット。test1は専用クラスタ内の空スキーマだけで、業務テーブル0件。
- 新規構築ランナーでV1/V2通常→V3省略登録→V4/V5通常、初期講師コマンドを実行した。適用先はこの専用クラスタのみ。
- Spring Boot：127.0.0.1:51830、現在のJAR。通常target=2を変更せず、DB URL/HTTP portを起動時だけ指定した。
- Next：localhost:51831。現在のfrontendを一時コピーして専用API URLでWebpack build/start。元の.env.localはコピーせず、現在の業務コードを変更していない。
- Chrome：headless、新規専用プロファイル、1440×1100、CDPで入力・選択・クリック・撮影した。System Events、アクセシビリティ、OS画面収録を使用していない。
- 全撮影は実Next→実Spring Boot→実PostgreSQL。今回API応答をmock/stub化していない。画面ID/E番号札・偽の確認UIを追加していない。

合成社員は氏名が全て「合成」で始まり、社員IDは全て撮影用の接頭辞を持つ。メールは今回入力・表示するデータとして登録していない。既存UIの氏名プレースホルダーは入力例であり、実在社員を入力したものではない。

初期データとして講師2人・新人等を作り、画面から追加社員と別の研修も登録した。補完撮影では同じ専用クラスタと構築済みアプリを再利用し、クラスタ/DBを増やしていない。再ログイン用の乱数パスワード更新は専用クラスタ内のみ。資格情報はstdin/メモリで扱い、引数・Git・報告へ平文保存していない。

## 3. 撮影結果と41枠の対応

撮影素材は初回26枚＋補完15枚＋対象領域の追加2枚の計43種類。対象領域の再撮影時に撮り直した中間画面4枚は重複加算していない。素材枚数とExcel内の画像枠数は別の数え方で、Home等の同じ状態は複数枠で再利用した。追加のエラー/参照専用/解除確認等の素材すべてを別枠でExcelへ追加したわけではなく、既存52枠を維持した。

| シート | 差し替え枠数 |
| --- | ---: |
| LGI_画面1 | 3 |
| HOM_画面1 | 5 |
| MMB_画面1 | 6 |
| SCD_画面1 | 5 |
| SCD_画面2 | 12 |
| PRM_画面1 | 10 |
| 合計 | 41 |

全て合成講師の認証済み状態で撮影し、ログイン/自己登録廃止の確認だけは未ログイン。参照専用素材は別の合成参照講師で撮影した。元画像の図形IDは対応Drawing内のIDであり、ブック全体で単独一意ではない。

| シート | Excel画像パート/図形 | 旧用途 | 新素材名 | 実際の撮影条件・状態 | 注意/未撮影 |
| --- | --- | --- | --- | --- | --- |
| LGI_画面1 | xl/media/image1.jpg / 図形ID 2 | E01 初期表示 | login-empty.png | 未ログイン、空の入力欄 | 現在の実画面を撮影。元のE番号札等は追加しない |
| LGI_画面1 | xl/media/image2.jpg / 図形ID 3 | E02 未登録者向け新規登録 | register-disabled.png | 自己登録はログインへ戻る | 廃止された自己登録の誘導画像を、現行のログインへの遷移へ置換。旧説明の見直し候補 |
| LGI_画面1 | xl/media/image3.jpg / 図形ID 4 | E03 ログイン | home-zero.png | 合成課題集計、ピンなし | 現在の実画面を撮影。元のE番号札等は追加しない |
| HOM_画面1 | xl/media/image5.jpg / 図形ID 2 | E01 Home初期表示 | home-zero.png | 合成課題集計、ピンなし | 現在の実画面を撮影。元のE番号札等は追加しない |
| HOM_画面1 | xl/media/image6.jpg / 図形ID 3 | E02 メンバー管理へ遷移 | members-list.png | 合成社員一覧 | 現在の実画面を撮影。元のE番号札等は追加しない |
| HOM_画面1 | xl/media/image7.jpg / 図形ID 4 | E03 スケジュールへ遷移 | schedule-list.png | 合成研修一覧 | 現在の実画面を撮影。元のE番号札等は追加しない |
| HOM_画面1 | xl/media/image8.jpg / 図形ID 5 | E04 進捗管理へ遷移 | progress-list.png | 合成課題一覧、未提出 | 現在の実画面を撮影。元のE番号札等は追加しない |
| HOM_画面1 | xl/media/image9.jpg / 図形ID 6 | E05 ログアウト | logout-after.png | 実ログアウト後、ネイティブ確認は未撮影 | 実ログアウト後。ネイティブ確認の画像は未撮影 |
| MMB_画面1 | xl/media/image13.jpg / 図形ID 2 | E01 メンバー初期表示 | members-list.png | 合成社員一覧 | 現在の実画面を撮影。元のE番号札等は追加しない |
| MMB_画面1 | xl/media/image14.jpg / 図形ID 3 | E02 メンバー行選択 | members-selected.png | 合成新人の選択 | 現在の実画面を撮影。元のE番号札等は追加しない |
| MMB_画面1 | xl/media/image15.jpg / 図形ID 4 | E03 新規登録モード | members-new.png | 新規入力 | 現在の実画面を撮影。元のE番号札等は追加しない |
| MMB_画面1 | xl/media/image16.jpg / 図形ID 5 | E04 登録・更新 | members-created.png | 合成社員の登録成功 | 現在の実画面を撮影。元のE番号札等は追加しない |
| MMB_画面1 | xl/media/image17.jpg / 図形ID 6 | E05 削除 | member-deleted.png | 合成社員を実削除した後。ネイティブ確認は未撮影 | 合成社員の実削除後。ネイティブ確認の画像は未撮影 |
| MMB_画面1 | xl/media/image18.jpg / 図形ID 7 | E06 Homeへ戻る | home-zero.png | 合成課題集計、ピンなし | 現在の実画面を撮影。元のE番号札等は追加しない |
| SCD_画面1 | xl/media/image22.jpg / 図形ID 2 | E01 一覧初期表示 | schedule-list.png | 合成研修一覧 | 現在の実画面を撮影。元のE番号札等は追加しない |
| SCD_画面1 | xl/media/image23.jpg / 図形ID 3 | E02 新規作成 | schedule-new.png | 新規基本情報の空欄 | 現在の実画面を撮影。元のE番号札等は追加しない |
| SCD_画面1 | xl/media/image24.jpg / 図形ID 4 | E03 スケジュール行選択 | schedule-detail.png | 合成研修の編集画面 | 現在の実画面を撮影。元のE番号札等は追加しない |
| SCD_画面1 | xl/media/image25.jpg / 図形ID 5 | E04 Homeへ戻る | home-zero.png | 合成課題集計、ピンなし | 現在の実画面を撮影。元のE番号札等は追加しない |
| SCD_画面1 | xl/media/image26.jpg / 図形ID 6 | E05 ピン留め切替 | home-two.png | 合成研修ピン2件 | ピン2件の状態。3件目警告は今回未撮影 |
| SCD_画面2 | xl/media/image28.jpg / 図形ID 2 | E01 新規基本情報 | schedule-new.png | 新規基本情報の空欄 | 現在の実画面を撮影。元のE番号札等は追加しない |
| SCD_画面2 | xl/media/image29.jpg / 図形ID 3 | E02 基本情報決定 | new-basic-decided.png | 実際に基本情報を決定 | 現在の実画面を撮影。元のE番号札等は追加しない |
| SCD_画面2 | xl/media/image30.jpg / 図形ID 4 | E03 基本情報解除 | new-basic-released.png | 実際に基本情報を解除 | 現在の実画面を撮影。元のE番号札等は追加しない |
| SCD_画面2 | xl/media/image31.jpg / 図形ID 5 | E04 権限追加 | new-permission-added.png | 画面から合成新人権限を追加 | 現在の実画面を撮影。元のE番号札等は追加しない |
| SCD_画面2 | xl/media/image32.jpg / 図形ID 6 | E05 分類追加 | new-category-focused.png | 実追加した合成分類の入力領域を中心に撮影 | 分類追加後、対象領域へスクロールして再撮影 |
| SCD_画面2 | xl/media/image33.jpg / 図形ID 7 | E06 内容操作エリア表示 | new-content-focused.png | 実入力した合成内容・分類・講師の領域を中心に撮影 | 分類・講師・内容を設定し、入力行を中心に再撮影 |
| SCD_画面2 | xl/media/image34.jpg / 図形ID 8 | E07 新規登録 | schedule-ui-created.png | 画面で新規登録して実API保存後の詳細 | 現在の実画面を撮影。元のE番号札等は追加しない |
| SCD_画面2 | xl/media/image35.jpg / 図形ID 9 | E08 登録済み更新 | schedule-updated.png | 名称保存後の状態 | 現在の実画面を撮影。元のE番号札等は追加しない |
| SCD_画面2 | xl/media/image36.jpg / 図形ID 10 | E09 削除 | schedule-delete-confirm.png | 現行のスケジュール削除確認ダイアログ | 実アプリのDOM確認ダイアログを撮影。偽の確認UIではない |
| SCD_画面2 | xl/media/image37.jpg / 図形ID 11 | E10 クリア | schedule-cleared.png | 取得時の値へ戻る | 現在の実画面を撮影。元のE番号札等は追加しない |
| SCD_画面2 | xl/media/image38.jpg / 図形ID 12 | E11 月切替 | schedule-next-month.png | 実際に次月へ切替。内容エリアへスクロール | 次月へ実切替し内容領域へスクロールして撮影 |
| SCD_画面2 | xl/media/image39.jpg / 図形ID 13 | E12 一覧へ戻る | schedule-list.png | 合成研修一覧 | 現在の実画面を撮影。元のE番号札等は追加しない |
| PRM_画面1 | xl/media/image42.jpg / 図形ID 2 | E01 進捗初期表示 | progress-list.png | 合成課題一覧、未提出 | 現在の実画面を撮影。元のE番号札等は追加しない |
| PRM_画面1 | xl/media/image43.jpg / 図形ID 3 | E02 検索 | progress-searched.png | 合成研修1で実検索 | 現在の実画面を撮影。元のE番号札等は追加しない |
| PRM_画面1 | xl/media/image44.jpg / 図形ID 4 | E03 条件クリア | progress-cleared.png | 検索条件を実クリア | 現在の実画面を撮影。元のE番号札等は追加しない |
| PRM_画面1 | xl/media/image45.jpg / 図形ID 5 | E04 課題行選択 | progress-selected.png | 合成課題の詳細 | 現在の実画面を撮影。元のE番号札等は追加しない |
| PRM_画面1 | xl/media/image46.jpg / 図形ID 6 | E05 課題状況更新 | progress-saved.png | 課題状況保存の更新しました | 現在の実画面を撮影。元のE番号札等は追加しない |
| PRM_画面1 | xl/media/image47.jpg / 図形ID 7 | E06 コメント追加 | comment-added.png | 本人の合成コメント追加 | 現在の実画面を撮影。元のE番号札等は追加しない |
| PRM_画面1 | xl/media/image48.jpg / 図形ID 8 | E07 コメント編集 | comment-edit.png | 本人コメントの編集入力欄 | 現在の実画面を撮影。元のE番号札等は追加しない |
| PRM_画面1 | xl/media/image49.jpg / 図形ID 9 | E08 コメント削除 | comment-deleted.png | 本人コメント削除後、ネイティブ確認は未撮影 | 本人コメントの実削除後。ネイティブ確認の画像は未撮影 |
| PRM_画面1 | xl/media/image50.jpg / 図形ID 10 | E09 Homeへ戻る | home-zero.png | 合成課題集計、ピンなし | 現在の実画面を撮影。元のE番号札等は追加しない |
| PRM_画面1 | xl/media/image51.jpg / 図形ID 11 | E10 一覧スクロール | progress-scrolled.png | 課題一覧の内部をスクロール | 課題一覧の内部スクロール位置を変更して撮影 |

## 4. 成功通知・入力エラー・確認・参照専用

撮影できた状態：メンバー登録成功、パスワード未入力エラー、名称更新成功、名称必須エラーの赤枠/赤文字、課題状況保存の「更新しました」、本人コメント追加/編集/削除後、参照講師の名称/課題更新非活性、ピン0/1/2件、新規基本情報決定/解除、権限・分類追加、実画面からのスケジュール登録、検索/クリア/一覧スクロール/月切替。

スケジュール削除確認と新規基本情報解除確認は実アプリのDOMダイアログを撮影できた。ネイティブ確認はCDPイベントとして実際の開閉を検出して受諾したが、画像には写していない。画像を生成・描き足して再現していない。

撮影だけの検証であり、更新競合や認可攻撃等の全回帰テストを再実施した結果ではない。JUnit・フロント全テストは今回未実行。Maven package（-DskipTests）と一時frontendのWebpack build/startは成功した。

## 5. 合成データ・秘密情報の確認

最終DB監査：

| 項目 | 結果 |
| --- | --- |
| 社員 | 5件。全件合成氏名/撮影用IDであることをSQLのbool_andで確認 |
| パスワード | 全件BCrypt形式。値は出力しない |
| スケジュール | 3件、全て撮影用。画面から追加登録した別研修は削除撮影後に存在しない |
| 明細 | 43件 |
| コメント | 0件。今回追加した合成コメントだけを本人が削除した |
| 専用publicのFlyway | V1〜V5成功履歴 |
| 専用test1の業務テーブル | 0件 |

撮影素材43種類のコンタクトシート/原寸相当の目視、ローカルOCR43枚を実施。メール/個人ホーム・一時パス/代表的なCookie・トークン形式のOCR検出は0件。ただしOCRだけで不存在を保証しない。入力済み社員の実在性は新規DBの合成データ監査で確認した。

JPEG化後のEXIFはsipsが付けた画素寸法・色空間、JFIF密度等で、ImageIOにGPS/Artist/Author/UserComment等の個人識別項目は検出しなかった。旧画面の個人情報をモザイクで隠して使い回したものではなく、撮影し直した実画面である。

## 6. バックアップと編集方法

編集前にGit管理外の専用作業領域へbasic-before.xlsxをコピーし、ファイル権限600で保持した。作業領域は700。バックアップには旧画像が残るため、公開集合へ含めない。

匿名化した参照名：`${PRIVATE_BACKUP_DIR}/basic-before.xlsx`。当該変数はバックアップの非公開保管場所を示す表記で、アプリの設定ではない。一時領域の長期保存は保証しない。既存バックアップを削除していない。

- 編集前SHA-256：`cfa47ecc67b775f2524bc403d0d6adddb5655752486d0ec4c2ea472dbb58bc3c`
- 編集後SHA-256：`9171b83e64fd57a86dbb2e1fb2dadc41f2d5bae32a9fcb02337ea3ed88763fec`

元のJPEGパート名・Content Types・Relationships・図形ID・アンカー・位置・サイズを保つため、新しいPNGをJPEGへ変換した。撮影画面1440×1100を変形・切り抜きせず、白余白を付けて2000×1125（16:9）に収めた。元の枠の縦横比に合わせるための余白であり、アプリUIを追加したものではない。文字は小さくなるため、最終の印刷可読性確認は必要。

Excel編集ライブラリによる全体保存はせず、ZIP内のJPEG41パートだけを入れ替えた。XML/セル値は一切変更していない。JPEG変換の初回はsipsの複合操作で出力がPNG形式のままになったため、形式マジックの検査で停止した。Excelへ書き込む前に二段階の変換へ直し、実JPEGであることを全件確認した。この補助処理の失敗を画像差し替え成功として数えていない。

## 7. Excel内部構造の検証

| 項目 | 前後比較 |
| --- | --- |
| 基本詳細設計書 | 39シート、名前/順序保持 |
| テーブル定義書 | 10シート、ファイル全体のSHA-256不変 |
| 画像パート | 52個維持。JPEG41個だけ変更、既存PNG11個は全件バイト一致 |
| ZIP内パート | 121個、名前/順序維持 |
| 変更パート | xl/media/image*.jpgの対象41件のみ |
| その他のパート | 80件、全件バイト一致 |
| 基本詳細のセル結合 | 702件保持 |
| 基本詳細の数式 | 0件のまま |
| テーブル定義の数式/結合 | ファイル不変により215件/1,777件維持 |
| 名前定義 | 基本詳細2件、保持 |
| 内部Relationship | 105件、参照欠落0 |
| Drawing/図形/画像ID/アンカー/トリミング指定 | 全Drawing/XMLがバイト一致 |
| 列幅/行高/書式/セル値/印刷設定 | worksheet/styles/workbook等がバイト一致 |
| ZIP CRC/XML整形式 | 成功 |
| 旧41画像のバイト一致データ | ブックのmedia内に残存0件 |

画像を外したり旧画像を別パートへ隠したりせず、既存の画像パートの内容を更新した。

## 8. Microsoft Excel確認

更新後ブックと同一内容の専用コピーbasic-updated.xlsxをMicrosoft Excelで読み取り専用で開き、39ワークシートを取得できた。保存せず専用コピーだけ閉じた。元ファイルや他の作業中ブックをExcelから保存していない。

ただし、System Events/画面収録等を使っていないため、Excelアプリ上で全画像を目視したものではない。画像欠け・重なり・細字の読みやすさ、全39シートの印刷プレビューは未確認。構造一致と「開けた」を、全レイアウト合格とは扱わない。

## 9. 仕様・説明文の修正候補（今回はセル変更なし）

1. LGI image2：旧「未登録者を新規登録へ誘導」は廃止。未ログイン自己登録は禁止し、初期講師はローカル管理コマンド、以後は講師のメンバー管理という説明へ統一する候補。
2. HOM image9/MMB image17/PRM image49：旧画像が確認ダイアログを説明していた場合、今回の画像は操作後である。「確認表示」ではなく「操作完了後」にキャプションを合わせるか、別途ネイティブ確認を撮影する必要がある。
3. Homeへの戻りは共通Homeナビ、詳細から一覧は現行の一覧リンクで説明する。専用戻るボタンを新設しない。
4. SCD image26：現在のピンアイコン/ピン2件状態を示す。3件目警告の画像は今回追加していない。
5. SCD image32/33：対象領域へスクロールした素材に更新した。全画面の上部も一枚に収まる画像ではないため、部分表示であることを説明し、印刷可読性を確認する。
6. 現行画面にはE番号や旧イベント用の表示札は存在しない。画像へ札を描き足さず、必要ならExcel側のキャプションでイベント番号を示す案をレビューする。

これらは承認済みの業務仕様を変更する提案ではなく、現在の実画面と説明を整合させる候補。セル値保持の制約に従い、今回は文言を変更していない。

## 10. 補助試行の失敗・保護・残作業

- 補完撮影の初回は一時BCrypt補助のlogging JAR不足で停止。既存JARをクラスパスへ補った。依存定義やアプリを修正していない。
- 次の試行は終了済みChromeのDevToolsポートファイルを参照して停止。既存プロファイルを削除せず、新規専用プロファイル名で再試行した。
- 対象領域の補完では、数値で始まるARIAラベルのCSS選択に失敗した試行もあった。補助側の選択方法を修正し、内容行を撮影できた。アプリ不具合と扱っていない。
- 最終の初回・補完・対象領域撮影は正常終了した。失敗試行を撮影成功に加算していない。
- 最初のローカルOCRは制限環境で当初の41件とも処理失敗。許可された同じローカルOCRの再実行と追加素材の再検査で43件を処理できた。画像を外部サービスへ送信していない。
- 専用Next/Spring Boot/Chromeは終了し、専用PostgreSQLは最後のデータ監査後もfast shutdownで正常停止した。専用データとログ、原PNG、対応manifestはGit外に保持している。ブラウザプロファイル/DBデータ/資格情報を公開しない。
- `${NON_DISTRIBUTED_EVIDENCE_DIR}`は今回のGit外証跡領域を示す匿名化表記で、リポジトリから取得できるファイルではない。原PNG名とハッシュは下表で対応させる。

公開前には、上記の説明候補と印刷可読性をレビューし、追加した下部領域の部分表示も含めて確認する。過去コミットの旧画像とメタデータは今回のファイル更新だけでは消えないため、Git履歴整理・公開集合の最終監査も残る。Public化可能という最終判断は引き続き保留する。

## 付録：画像ごとの原PNG SHA-256

| Excel画像 | 原PNG素材 | 原PNG SHA-256 |
| --- | --- | --- |
| xl/media/image1.jpg | login-empty.png | 959e17c6b7df1395ca0a864acf024eb3a08aa5d16f9366652e00ee74a0d207f8 |
| xl/media/image2.jpg | register-disabled.png | 959e17c6b7df1395ca0a864acf024eb3a08aa5d16f9366652e00ee74a0d207f8 |
| xl/media/image3.jpg | home-zero.png | 4c87f583334958fa947d995704e8bf96c91966e900053a8d22a2d61d1318ba90 |
| xl/media/image5.jpg | home-zero.png | 4c87f583334958fa947d995704e8bf96c91966e900053a8d22a2d61d1318ba90 |
| xl/media/image6.jpg | members-list.png | 74fad45f7c49a91987607c34ea3e616de5d6a08176f8ef71d60d9e5875342cd2 |
| xl/media/image7.jpg | schedule-list.png | 878541aba570a23eb02937edcb58f939531abfa9ffaeeff8d064054b04038eea |
| xl/media/image8.jpg | progress-list.png | 4f0cc085269ecf9cb734cf0d1bea6904bb2d4ebe04df7243be651f4a7fc74a67 |
| xl/media/image9.jpg | logout-after.png | 959e17c6b7df1395ca0a864acf024eb3a08aa5d16f9366652e00ee74a0d207f8 |
| xl/media/image13.jpg | members-list.png | 74fad45f7c49a91987607c34ea3e616de5d6a08176f8ef71d60d9e5875342cd2 |
| xl/media/image14.jpg | members-selected.png | 54c8e24f46663d8dfbc16c8c1cfc14bf518735d13c71de52bd16ca73c0733b05 |
| xl/media/image15.jpg | members-new.png | da5d7a7da37a2cf48483dd19778e8fa67cf06c436f41c157f7cfc7f3f6b20566 |
| xl/media/image16.jpg | members-created.png | 0fcd76e20d774346e69a70bb3ba4bbcf56c58641b8bc20b4640078af96496bef |
| xl/media/image17.jpg | member-deleted.png | 8b97581e0dd93de7648c93c1399db4afa90b12e7d58f1e2a964d6590464ac616 |
| xl/media/image18.jpg | home-zero.png | 4c87f583334958fa947d995704e8bf96c91966e900053a8d22a2d61d1318ba90 |
| xl/media/image22.jpg | schedule-list.png | 878541aba570a23eb02937edcb58f939531abfa9ffaeeff8d064054b04038eea |
| xl/media/image23.jpg | schedule-new.png | b71f1968116eec1bcac179b1d0ca269104ebd7be587b46bea5f1be7626f5fb9c |
| xl/media/image24.jpg | schedule-detail.png | 146eedebda7d3438976f9ec1e759146749ebd0cc7f6315942155b7a50d686088 |
| xl/media/image25.jpg | home-zero.png | 4c87f583334958fa947d995704e8bf96c91966e900053a8d22a2d61d1318ba90 |
| xl/media/image26.jpg | home-two.png | c0eba014bbd9e876c746f622c7aa9425e990f786e0872ea2633533b6a66e85fd |
| xl/media/image28.jpg | schedule-new.png | b71f1968116eec1bcac179b1d0ca269104ebd7be587b46bea5f1be7626f5fb9c |
| xl/media/image29.jpg | new-basic-decided.png | 7d0611b808a9de50443484aa705eb26be752e7245c6b7bf843ba4d137a1a6608 |
| xl/media/image30.jpg | new-basic-released.png | 62ddf7372aa53130e7e63d754b81ac02a93ad88c96df7bc5031a1ddaf1b99675 |
| xl/media/image31.jpg | new-permission-added.png | a803afe5b9a5e50f730da621644135ee29f35b3836f05c978cadc7ac9d082ac7 |
| xl/media/image32.jpg | new-category-focused.png | e42264a3e0c66beffbd28b1d02730a27fba31107ccc0094a77efd89afdb20aaf |
| xl/media/image33.jpg | new-content-focused.png | 3149afdad8a0a75f688383943c7a231cd9b5c631b7d631e642f78706d8c97a38 |
| xl/media/image34.jpg | schedule-ui-created.png | 3b318798604c98532be54b4560eb267061766c7b5f03934e1e9f95806ab5519b |
| xl/media/image35.jpg | schedule-updated.png | cbb8028a511b3b00d03fbfd65232e4a4bbabc6a34a01c590f30363254553f8b1 |
| xl/media/image36.jpg | schedule-delete-confirm.png | fc27b310d0d3ca646a7425a2923d1cbcaef9587caa496d0b5eb1240dca5d5352 |
| xl/media/image37.jpg | schedule-cleared.png | b28c67fd7dc40eb62b2397bda0a56999efa145d8c0f995308ef955869908c135 |
| xl/media/image38.jpg | schedule-next-month.png | 7b9981011bf8ad94890b6739b3287896a0605916c16192a085f73ad5445ed017 |
| xl/media/image39.jpg | schedule-list.png | 878541aba570a23eb02937edcb58f939531abfa9ffaeeff8d064054b04038eea |
| xl/media/image42.jpg | progress-list.png | 4f0cc085269ecf9cb734cf0d1bea6904bb2d4ebe04df7243be651f4a7fc74a67 |
| xl/media/image43.jpg | progress-searched.png | a1ff6d406cb42748aada207a4752729e80910ddd25f6108fdc9551914c73e086 |
| xl/media/image44.jpg | progress-cleared.png | 4f0cc085269ecf9cb734cf0d1bea6904bb2d4ebe04df7243be651f4a7fc74a67 |
| xl/media/image45.jpg | progress-selected.png | e8c4954f3b54e03d5f1df550fe083004daea1230e91ab166cc342e8ca7ca1b06 |
| xl/media/image46.jpg | progress-saved.png | 97cecc333b2469b0122ae5cf9ca9cf886f94c16f378f7edde16ee57df9fd8e22 |
| xl/media/image47.jpg | comment-added.png | 7ef46ee134e497979151fbaca194ea12e7d861e10e319377cbd351df7e2aa45a |
| xl/media/image48.jpg | comment-edit.png | c8cc460cb43f687b6c35549254b30a3f54ee2cb25d148953ea0714272937c11c |
| xl/media/image49.jpg | comment-deleted.png | 97cecc333b2469b0122ae5cf9ca9cf886f94c16f378f7edde16ee57df9fd8e22 |
| xl/media/image50.jpg | home-zero.png | 4c87f583334958fa947d995704e8bf96c91966e900053a8d22a2d61d1318ba90 |
| xl/media/image51.jpg | progress-scrolled.png | 64cd9b40ca92f61e8cbb301604df43bbec6c83884a7040d6184fa8e31ac73f39 |

最終検証：開始時278ファイルのうち基本詳細設計書以外は全件SHA-256一致。追加は本報告書だけ。最終ExcelはMicrosoft Excelで確認した専用コピーとバイト一致。ZIP変更は対象JPEG41パートのみ、Gitインデックス不変、git diff --check成功。
