# PostgreSQLライフサイクル修正・隔離起動試験（2026-10-12）

## 原因調査

既存training-v4-guarded-write-001と専用volumeは読み取りだけで調査し、起動状態や内容を変更していない。
既存PostgreSQLログのUTC時刻：

| 時刻 | 記録 |
| --- | --- |
| 10:38:07.634 | PostgreSQL17.11開始 |
| 10:38:07.638 | ready to accept connections |
| 10:38:07.787 | received fast shutdown request |
| 10:38:07.833 | database system is shut down |

直接確認した停止原因は、起動完了から149ms後に受けたfast shutdown要求。異常クラッシュのログではない。
旧entrypointはpg_ctl start後、EXIT/HUP/INT/TERMトラップにpg_ctl stop -m fastを登録し、末尾で/bin/shを呼んでいた。
補助スクリプトはこれをdocker exec -diで起動していた。既存コンテナのPID1はsleep、DB管理シェルは調査時点で消失していた。
終了済みpostgresのプロセスエントリだけが残っていた（ログではサーバー停止済み）。

最も可能性の高い根本原因は、補助シェルの標準入力終了→/bin/shの終了→親シェルのEXITトラップ→fast shutdown。
ローカルのDBなし試験で、stdin=DEVNULLの/bin/shが終了しEXITトラップが作動することを再現した。
当時のシグナル送信元や標準入力イベントの監査記録はないため、当時のトラップ実行そのものを断定したものではない。

## 修正ファイル

- tools/flyway/docker-isolation/entrypoint.sh
- tools/flyway/docker-isolation/lifecycle.py（新規）
- tools/flyway/docker-isolation/Dockerfile
- tools/flyway/docker-isolation/prepare.py
- tools/flyway/docker-isolation/test_lifecycle_docker.py（新規）
- docs/verification/schedule-content-v4-lifecycle-test.md（本報告）
- docs/verification/schedule-content-v4-docker-plan.md（現在の起動方式への参照を追加）

network_guard.pyは変更していない。既存Flywayランナー2個、V1〜V4、application.propertiesも編集していない。

## 新しい管理方式

entrypointはexec python3 -B lifecycle.pyでPID1をPython管理プロセスにする。
対話シェル・標準入力・docker execの寿命に依存しない。通常起動ではdocker exec -diでentrypointを起動しない。

1. ガードを実行し、新規データ領域が空であることを確認。
2. guard-passed-awaiting-host-inspectを記録し、最大300秒、開始合図を待つ。
3. 起動側がDocker inspectで分離条件を確認してから/evidence/start.requestにstartと改行を書き込む。
4. ガードを再検証し、initdbを実施。
5. postgresをフォアグラウンドの直接子プロセスとして起動し、最大30秒でpg_isready成功を確認。
6. 新規クラスタ内だけに一般ロールtraining_portal_userと空のtraining_portalを作成。
7. 100ms間隔で子プロセス終了を監視。要求していない終了ならイベントを記録し、管理プロセスは70で終了。
8. SIGINT/SIGTERMではPostgreSQLへSIGINTを送り、最大20秒でクリーンなfast shutdownを待つ。正常終了は0。

失敗時も子プロセスを停止してログを保持する。停止待ち失敗時は記録して強制終了するが、成功扱いしない。
通常のfast shutdownは接続を終了し未完了トランザクションをロールバックする方式で、クラッシュ停止とは区別する。

初期化・PostgreSQL・DB作成・ライフサイクルのログを/evidenceへ権限を制限して保存する。
隔離専用の乱数パスワードは引数やログに出さず、/evidence/runtime-credentials.jsonへ600で保管する。ホストの認証情報は消去してから起動する。
DB初期化SQLは標準入力で渡し、サーバーのstatementログ出力も抑制する。エラーは固定イベントだけをコンテナ標準出力へ記録する。
ready.jsonは起動時のPID記録であり、現在の稼働保証ではない。稼働判定はDocker状態とpg_isreadyを併用する。

## 新イメージ

タグ：training-v4-isolation:lifecycle-v1

固定イメージID：sha256:818fa78ae2a6145001eb46a2a53759dbca8c5b8d5931f088694702e7ac789531

前回と同じPostgreSQL/Temurinベースdigestを使用。旧イメージは削除・上書きしていない。
ビルド時にPostgreSQL17.11、Java21.0.12.1、Python3.11.2、JAR7個のハッシュ一致、既存ランナーコンパイルを確認した。

## 実コンテナ試験

実行コマンド：
```
python3 tools/flyway/docker-isolation/test_lifecycle_docker.py --execute-isolated-lifecycle-tests
```

新コンテナ：
- training-v4-lifecycle-normal-001
- training-v4-lifecycle-crash-001

各コンテナに新規の<コンテナ名>-dataと<コンテナ名>-evidence volumeを使用。
既存volumeや既存コンテナは使用していない。

両方ともnetwork none、ポート公開なし、非privileged、capability ALL削除、no-new-privileges、ルート読取専用。
共有は新規専用volume2個だけ。ホストソケット・バックアップ・ホストDB認証情報を渡していない。
実行用/tmpはtmpfs、継承されたPGDATAパスは読取専用tmpfsで覆った。
DB初期化前にガード成功とDocker inspect設定を確認し、初期化直前にもガードを実行した。

| 試験 | 結果 |
| --- | --- |
| 両コンテナのガード・設定照合 | 成功 |
| initdb・PostgreSQL起動 | 成功 |
| pg_isready | 成功 |
| PID1がPython管理プロセス | 確認 |
| 補助シェルを3回起動し終了、各1秒後に確認 | DBが稼働し続けた |
| docker stopで正常停止 | normal-stop-complete、ExitCode=0 |
| 新規crashコンテナのPostgreSQL子PIDだけをSIGKILL | postgres-unexpected-exit、ExitCode=70 |

異常終了試験に使ったデータ領域は正常系と別。SIGKILLによりその領域はクリーン停止ではないため、正常系の保存領域と区別する。
両コンテナとも試験終了時点で停止済み。コンテナ・volume・イメージは削除せず保持している。

ログコピー：${NON_DISTRIBUTED_EVIDENCE_DIR}
元ログは各専用evidence volumeにも保持。資格情報ファイルはホストへコピーしていない。

ガード単体テスト40件、Python構文、シェル構文、git diff --checkも成功。
MavenのDB統合テストは実行していない。

## 残るリスクと再開判断

- 起動維持試験は短時間。長時間運転・全シグナル・停止タイムアウトの強制終了経路までは未検証。
- 監視はpostmasterの終了を検知する。プロセスが生存したまま応答しなくなる状態を継続監視する仕組みは未追加。開始時のreadinessは検証済み。
- 起動は空の新規領域だけを許可する設計。既存データ領域での再起動は自動許可しない。
- 開始合図ファイルはホスト側の設定確認を代替しない。提供したテストスクリプトと同様に、inspect成功後にだけ作成する。
- 旧${NON_DISTRIBUTED_EVIDENCE_DIR}/previous-workspace
- DB復元・修正済みFlywayランナーの書き込みは今回未実施。開発用public適用の可否はまだ判定しない。

新規専用領域と固定イメージを使用し、起動側設定照合・ガード成功を再確認する条件で、次段階の隔離DB復元/Flyway検証を再開できる。
今回はホスト開発DB・既存検証DBへ接続せず、バックアップ復元・Flyway・Spring Bootを実行していない。
commit/push/mergeは行っていない。

## 公開用の参照整理（検証の再実行なし）

`${NON_DISTRIBUTED_EVIDENCE_DIR}`と`${PRIVATE_BACKUP_DIR}`は、Git管理外で保管した当時の非配布証跡・バックアップの保存先を示す匿名表記であり、実行に必要な環境変数ではない。本文の検証結果・ハッシュ値・失敗試行・未実施範囲は当時の記録として維持し、この参照整理に伴う検証の再実行は行っていない。
