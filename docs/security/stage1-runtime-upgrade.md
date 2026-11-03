# セキュリティ更新第1段階（検証候補）

本書は第1段階の記録です。末尾の第2段階未対応という記述は当時の状態を示します。現在の統合候補では[第2段階](stage2-authentication.md)と[第3段階](stage3-defense-and-audit.md)も反映しています。本番環境での検証や未承認のパスワード方針は別途残ります。

Node.js 24.22.1、Eclipse Temurin 21.0.13+9で検証する。OS全体の実行環境は変更せず、プロジェクト専用配置からJAVA_HOME/PATHをプロセス単位で指定する。.java-versionは版のみを示し、JDKベンダや追加CA対策まで保証しない。

Next.js 16.4.1、React/ReactDOM 19.3.1、sharp 0.35.5を使用する。Spring Boot 4.1.1のBOMを基準とし、対策版を取り込む安定BOMが確認できなかったため、Tomcat 11.0.27、Jackson BOM 3.1.7、pgJDBC 42.7.14を親POMの管理プロパティで指定する。Flyway 12.4.0と既存マイグレーションは維持する。今後のBOM更新時にこれらの個別指定を再評価する。

## JDKの追加CA問題

[OpenJDK 2026-10-21](https://openjdk.org/groups/vulnerability/advisories/2026-10-21)では21.0.13以前もCVE-2026-91403の対象。JDK更新だけで解決したと扱わない。公式blocked.certsを取得・照合して専用JDKへ適用するか、公式の失効検証設定を採用し、影響と拒否動作を検証する必要がある。

公式の代替設定例：Java起動時に `-Dcom.sun.net.ssl.checkRevocation=true -Dcom.sun.security.enableCRLDP=true`。CRLへの到達性と正常なTLS接続への影響も確認する。これらのフラグを書くだけでは失効拒否試験の合格にならない。

公式blocked.certsの直接配布URLはHTTP 403となったため、OpenJDK注意喚起が参照するインシデント資料の公開証明書16件（8中間CAとクロス署名）について、DERのSHA-256を再計算し、提示値との一致を確認した。既存の遮断一覧を保持して作業専用JDKへ追加し、JDKの遮断判定が16件すべてfalseからtrueになり、他の信頼証明書118件の判定が変わらないことを確認した。OSのJDKは変更していない。この追加対策はプロジェクトファイルだけをコピーしても移行されないため、利用先の専用JDKでも適用・検証する。

## 適用範囲

独立作業コピーのみ。既存ブランチ・公開候補への反映は別作業。認証仕様・画面仕様・DB定義は変更しない。ログイン試行制限、パスワード変更後のセッション、HTTPS/Cookie等の第2段階項目も未対応。
