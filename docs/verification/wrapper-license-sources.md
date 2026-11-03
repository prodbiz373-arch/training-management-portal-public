# Maven Wrapper配布通知の取得記録（2026-11-03）

適用対象は`mvnw`と`mvnw.cmd`。Apache Maven Wrapper 3.3.4のonly-script公式テンプレートと、版プレースホルダー置換後の内容が一致する。既存の両スクリプトは変更していない。プロジェクト全体のライセンス採用ではない。

| 同梱文書 | 公式取得元 | SHA-256 | bytes |
| --- | --- | --- | --- |
| [apache-maven-wrapper-3.3.4-LICENSE.txt](../../licenses/apache-maven-wrapper-3.3.4-LICENSE.txt) | [maven-wrapper-3.3.4/LICENSE.txt](https://raw.githubusercontent.com/apache/maven-wrapper/maven-wrapper-3.3.4/LICENSE) | `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30` | 11358 |
| [apache-maven-wrapper-3.3.4-NOTICE.txt](../../licenses/apache-maven-wrapper-3.3.4-NOTICE.txt) | [maven-wrapper-3.3.4/NOTICE.txt](https://raw.githubusercontent.com/apache/maven-wrapper/maven-wrapper-3.3.4/NOTICE) | `706530c7c8baaa8dd4b37f3f12c87042c73b9d53051a0197770373516d85ec0f` | 394 |

取得日：2026-11-03。公式タグURLからHTTPSで再取得し、前回監査で保存した公式文書ともバイト一致を確認。LICENSE/NOTICE本文の編集・改行変換は行っていない。全文およびNOTICEの上流著作権表示を保持する。

| スクリプト | 公式テンプレート | 現行SHA-256 |
| --- | --- | --- |
| [mvnw](../../mvnw) | [only-script 3.3.4](https://raw.githubusercontent.com/apache/maven-wrapper/maven-wrapper-3.3.4/maven-wrapper-distribution/src/resources/only-mvnw) | `cae96cef89ebea3531221f4ae17c23cf8edf67d00eae8306d4186ae1bbed4d02` |
| [mvnw.cmd](../../mvnw.cmd) | [only-script 3.3.4](https://raw.githubusercontent.com/apache/maven-wrapper/maven-wrapper-3.3.4/maven-wrapper-distribution/src/resources/only-mvnw.cmd) | `4a361e1374a3e5ad6d03e18e9adc0cf181ac5058ac6203b76f0ba3b456b56481` |

再配布時は[第三者通知](../../THIRD_PARTY_NOTICES.md)と該当全文を伴わせる。既存コミットを単独取得した場合の不足は[履歴上の配布条件](historical-distribution-notes.md)に分離する。正式な通知方針とPublic化判断は承認待ち。
