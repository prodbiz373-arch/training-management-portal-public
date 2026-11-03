# Dockerネットワークガード修正・DBなし検証（2026-10-12）

## 結果

修正ガードを含む `training-v4-isolation:guard-v4` をビルドし、DB処理を呼ばない診断コンテナでExitCode=0を確認した。

最終イメージID：
`sha256:1d23e3e6be1b5530639a496dee8f88e28a93f017b0d88fe622c91550ae740336`

最終診断コンテナ：`training-v4-guard-v4-diag-002`

出力：
```
network guard PASS: cross-checks, DOWN tunnels, loopback-only addresses/routes; forwarding4=1
```

## 変更ファイル

- tools/flyway/docker-isolation/entrypoint.sh：DB初期化前に独立Pythonガードを呼ぶ。
- tools/flyway/docker-isolation/network_guard.py：新規。標準ライブラリのみ。DB・外部接続・コマンド実行APIなし。
- tools/flyway/docker-isolation/test_network_guard.py：新規。診断結果に基づくテスト。
- tools/flyway/docker-isolation/Dockerfile：ガードをコピーし、読み取り可能な権限を付与。
- tools/flyway/docker-isolation/prepare.py：ガードを限定ビルド入力に追加。
- docs/verification/schedule-content-v4-network-guard.md：本報告。

既存Flywayランナー2個・V1〜V4・application.propertiesは編集していない。
SQL/ランナーのハッシュはビルド用manifestと一致した。

## 判定条件

- sysfsのデバイスリンクだけを列挙。通常ファイルはbonding_mastersだけを管理ファイルとして認識し、未知項目は停止。
- sysfs、socket.if_nameindex、proc/net/dev、netlinkのデバイス名と番号を照合。型・flags・operstateも照合する。
- sysfsが表示しないvolatileなRUNNING/LOWER_UP/DORMANTビットだけをマスクしてnetlink flagsと比較する。
- loはloopback型、管理上UP、127.0.0.1/8と::1/128を必須とする。
- 非loは診断済みの名前/型の組だけを候補とし、DOWN、アドレスなし、下位リンク/masterなし、未設定トンネルの属性プロファイルとの完全一致も必須。
- トンネル属性は名前ごとのLinuxKit診断プロファイルを固定。未知・欠落・値の変更は停止。TTLなど正常な非ゼロ既定値を含む。
- netlinkで全経路表を取得し、ローカル表255の所定のIPv4ループバック3経路とIPv6 ::1経路だけを許可。gateway、multipath等の未対応属性は停止。
- netlinkは送信元・sequence・長さ・重複属性・切断・dump中断・終了statusを検査する。
- 情報取得・解析失敗、不一致では非ゼロ終了。例外の生メッセージや環境変数を出力せず、固定の段階/codeだけを出す。
- 連続する2回のスナップショットを検証し、判定に用いる情報が変化すれば停止。

IPv4 forwardingは0/1を認識するが、1であっても上記すべての条件を満たす場合だけ許可する。
IPv6 forwardingは診断基準の0だけを許可する。forwarding値単独や通信カウンター0は成功根拠にしない。

ガードはコンテナ内部の状態を検査する。Docker NetworkModeはコンテナから自己申告させない。
起動側でnone・ポート公開なし・cap追加なし・非privileged・読取専用・ソケット共有なしをinspectする運用を維持する。

## テスト

`python3 -B -m unittest discover -s tools/flyway/docker-isolation -p 'test_network_guard.py'`

40件成功。正常系はloのみ、診断済みトンネル構成、ループバック経路、bondingファイル除外、forwarding=0/1。
異常系は未知デバイス/型、UP、IPv4/IPv6アドレス、外部/別表経路、gateway属性、トンネル接続先/下位リンク、各情報不一致、未知/欠落属性、取得失敗、netlink不正長/重複/中断/切断/エラー、取得中の状態変化等。
未知デバイス試験は、情報源間で名前を一致させても拒否するケースを含む。
エラーに秘密情報を模した例外文言が含まれても出力しないことを確認した。
追加したnetlinkモック試験は最初macOSにLinux定数がないため4件エラーになった。テスト側にLinux定数をモックし、最終40件成功。実ネットワーク操作を行うモック試験ではない。
Python構文、sh -n、git diff --checkも成功。
MavenのDB統合テストは今回の禁止範囲に抵触し得るため実行していない。

## ビルドと実環境検証

固定ベースdigestは前回と同じ：

- postgres:17.11-bookworm：sha256:a85953d6f830fd55a12929df3b7a4fa94dc96d652de62d2d714e0867ccb3670d
- eclipse-temurin:21-jdk-jammy：sha256:3cecce1b41af0b39a980b7c8d955354fc9538435bd30127c3fe3ddc7f01029e3

PostgreSQL17.11、Java21.0.12.1、Python3.11.2、固定JAR7個ハッシュとランナーコンパイルをビルド時確認した。

途中のguard-v2は未知リンク属性67/68/69で安全側に停止した。
Linux公式実装を確認し、NETNS_IMMUTABLE(u8)、HEADROOM/TAILROOM(u16)として型を検査する対応を追加した。
最初のサイズ実装ではHEADROOM/TAILROOMをu32と誤って扱い、guard-v3はサイズ不一致で停止。公式実装に合わせて訂正した。
その後、アドレス由来IFA_PROTOで停止したため、IFAPROT_KERNEL_LO=1だけを許可する検証を追加した。
この間、停止したコンテナからDB処理へ進めていない。

参照した一次資料：
- https://github.com/torvalds/linux/blob/master/include/uapi/linux/if_link.h
- https://github.com/torvalds/linux/blob/master/net/core/rtnetlink.c
- https://github.com/torvalds/linux/blob/master/include/uapi/linux/if_addr.h

最終コンテナのinspect結果：network=none、接続先はnoneだけ、PortBindings={}、Privileged=false、CapAdd=null、CapDrop=[ALL]、ReadonlyRootfs=true、Mounts=[]。
継承PGDATAの場所には読取専用tmpfsを指定し、バックアップ・認証情報・ホストソケットを渡していない。
entrypointはpython3でガードだけを直接呼び、通常entrypointのDB処理は実行していない。

元のreviewedイメージIDはsha256:9f6115291804986277f4f8fb8672aa1e765d2988edaa64fda86ab4bdf29dd103のまま。
旧コンテナ・volume・イメージを削除していない。今回の診断コンテナと中間イメージも保持した。
最終ビルド入力・manifestは、アクセス制限した永続保存先run-004に保管。バックアップはcontext外で、診断にマウントしていない。

## リスクと再開条件

- このガードはLinuxKitで診断した保守的なプロファイルであり、カーネル更新や未知属性では停止し得る。根拠確認なしに許可範囲を拡張しない。
- 既知のリンクメタデータを認識するが、これは汎用のネットワーク設定監査ツールではない。実通信の許可判断はデバイス状態、全アドレス、全経路、トンネル属性の独立検証に依存する。
- 二重取得は取得中の変化検知であり、検査後のホスト管理操作を防ぐ機構ではない。Dockerのnone設定・cap削除・読取専用を維持する。
- 修正後のガードは成功したが、修正後FlywayランナーのDB書き込みはまだ未検証。
- 次の承認範囲で、最終固定イメージを使用した隔離DB検証を再開できる。起動直前にもDocker設定とガード結果を確認する。開発用public適用の可否はまだ判定しない。

今回、開発DB・既存検証DBへの接続、DB作成・復元、Flyway、Spring Boot起動は行っていない。
ホストDBの前後比較は行っておらず、他プロセスの変更有無まで実証したものではない。
commit/push/mergeは行っていない。
