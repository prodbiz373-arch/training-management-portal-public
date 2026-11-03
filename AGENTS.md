# 新人研修スケジュール・課題管理ポータル 開発指示

## 1. プロジェクト概要

新人研修で利用するWebポータルサイトを開発する。

主な機能は次のとおり。

- ログイン・ログアウト
- 研修メンバー管理
- 研修スケジュール管理
- 利用者ごとのスケジュール参照・編集権限管理
- スケジュールのピン留め
- 課題提出状況・レビュー状況・コメント管理
- 新人および講師向けホーム画面

現在は、動作するMVPを先に完成させる実装フェーズである。重大な設計矛盾、認証・認可、DB整合性、セキュリティを優先し、軽微なUI調整は後回しにする。

## 2. 採用技術

- Frontend: Next.js / React / TypeScript
- Backend: Spring Boot 4.1.1 / Java 21
- API: REST API / JSON
- Database: PostgreSQL 17
- ORM: Spring Data JPA / Hibernate
- Migration: Flyway
- Build: Maven Wrapper
- Package root: `com.training.portal`

フロントエンドとバックエンドを分離し、ReactからSpring BootのREST APIを呼び出す。

## 3. 仕様の優先順位

仕様判断は次の順番で行う。

1. 最新の基本・詳細設計書
2. 最新のテーブル定義書
3. プロジェクト内で確定済みの仕様
4. ユーザーからの最新指示
5. 一般的なWebシステム設計のベストプラクティス

主要な情報源は以下。

- `docs/design/基本詳細設計書.xlsx`
- `docs/design/テーブル定義書.xlsx`
- `website-design_ログインメンバー保存修正版.zip`

これらがリポジトリ外にある場合、内容を推測して既存仕様を変更しない。不足情報が実装結果を大きく変える場合は、実装前に確認する。

設計書と画面モックが異なる場合、設計書とテーブル定義書を優先する。画面モック内の`localStorage`とモックデータは本実装ではREST APIとDBへ置き換える。

## 4. 確定済みの主要仕様

### 権限

- 社員区分は`1: 新人`、`2: 講師`。
- 新人は閲覧権限のあるスケジュールと自分自身の課題のみ参照できる。
- 新人は管理系画面へアクセスできない。
- 講師は編集権限のあるスケジュールを管理し、対象スケジュールの新人の課題を確認できる。
- 画面上の表示制御だけでなく、API側でも必ず認可する。
- 権限不足はHTTP 403、対象なしはHTTP 404とする。

### ピン留め

- 専用の`schedule_pin`テーブルは作成しない。
- `schedule_can_edit.pin_flg`で利用者ごとのピン留め状態を管理する。
- 1利用者につき最大2件。
- 3件目は更新せず、警告を返す。
- ピン留め同士は`schedule_id`昇順。

### 認証・パスワード

- `kenshu_member`に登録された新人・講師を認証対象とする。
- パスワードは平文保存しない。Spring SecurityのPasswordEncoderを利用してハッシュ化する。
- パスワードをレスポンスDTO、ログ、例外メッセージへ含めない。
- DBパスワードをソースコードへ直接書かない。
- DBパスワードは環境変数`TRAINING_PORTAL_DB_PASSWORD`から取得する。

## 5. DB・Flywayルール

- Database: `training_portal`
- 通常開発用schema: `public`
- テスト用schema: `test1`
- 通常実行では`public`を使用する。
- 自動テストでは`test1`を使用し、`public`のデータを変更しない。
- テーブル・カラムの物理名はテーブル定義書に合わせる。
- PostgreSQLのテーブル・カラムコメントに日本語論理名を登録する。
- HibernateにDDLを自動生成させず、`spring.jpa.hibernate.ddl-auto=validate`を使用する。
- DB変更はFlywayマイグレーションだけで行う。
- 実行済みの`V1__create_tables.sql`は変更しない。
- 追加変更は`V2__...sql`以降の新しいファイルで行う。
- Flywayファイルを削除、改名、上書きしない。
- 外部キー、複合主キー、NOT NULL、CHECK制約、更新カウンタを維持する。

現在の主要テーブルは以下の8個。

1. `kenshu_member`
2. `schedule`
3. `schedule_can_edit`
4. `schedule_bunrui`
5. `schedule_details`
6. `issue_statuses`
7. `issue_submission_statuses`
8. `issue_submission_statuses_comment`

## 6. バックエンド実装規約

- 機能単位で`controller`、`service`、`repository`、`dto`、`entity`または同等の責務を分離する。
- ControllerからRepositoryを直接呼ばない。
- EntityをAPIレスポンスとして直接返さない。Request DTOとResponse DTOを使用する。
- 入力チェックにはJakarta Validationを使用する。
- 更新処理は`update_count`とJPAの`@Version`を使用して楽観ロックを行う。
- トランザクション境界はService層に置く。
- 例外処理は共通化し、クライアントへ内部例外やSQL情報を返さない。
- 日時はPostgreSQLの`TIMESTAMP`に対応する型として`LocalDateTime`を使用する。
- 命名は既存の物理名を尊重する。ただしJavaコードはlowerCamelCaseを使用する。
- Lombokは、導入を明示的に決定するまで追加しない。
- 不要な依存関係を増やさない。
- Thymeleafで画面を実装しない。画面はReact/Next.jsを使用する。

## 7. API実装方針

API仕様は実装しながら確定する。ただし、その場限りのエンドポイントを増やさない。

APIを追加・変更するときは、少なくとも以下を明確にする。

- HTTPメソッドとパス
- 認証・認可条件
- Request DTO
- Response DTO
- 正常時のHTTPステータス
- 400、401、403、404、409、500の使い分け
- 対応する画面イベント
- 参照・更新するテーブル
- トランザクション範囲

API設計が確定したら、READMEまたは`docs/`配下のAPI仕様書へ反映する。

## 8. テスト方針

- 変更後は最低限`./mvnw clean test`を実行する。
- コンパイル確認だけでよい狭い変更では`./mvnw clean compile`も利用できる。
- Serviceの業務ルール、権限、入力境界値、重複、楽観ロックをテストする。
- RepositoryやAPIのDB統合テストは`test1`スキーマを使用する。
- テストが失敗した状態で完了と報告しない。
- 実行できなかったテストがある場合は理由を明記する。

## 9. Git運用

- 通常作業ブランチは`develop`。
- 細かいfeatureブランチは原則作らない。
- `main`は完成版・安定版として扱う。
- 機能単位で小さくコミットする。
- コミット前に`git status`と差分を確認する。
- ユーザーの明示的な依頼がない限り、Codexはcommit、push、mergeを実行しない。
- `main`への直接push、履歴の強制更新、`git reset --hard`を行わない。
- パスワード、トークン、秘密鍵、個人情報をコミットしない。

## 10. Codexの作業手順

1. 変更前に関連ファイル、DBマイグレーション、テストを確認する。
2. 仕様を「確定事項」「推測」「提案」「要確認」に分ける。
3. 重大な不足だけを質問し、軽微な実装詳細は既存規約に合わせる。
4. 必要最小限の範囲を編集する。
5. フォーマット、コンパイル、テストを実行する。
6. 変更差分を自己レビューする。
7. 完了時に、変更ファイル、実装内容、テスト結果、残課題を日本語で報告する。

既存仕様を勝手に変更しない。変更が必要な場合は、次を明示してユーザーへ確認する。

- 現在の仕様
- 問題点
- 変更案
- 変更理由

## 11. 現在の未確定事項

以下は勝手に確定しない。

- 未ログイン新規登録で講師区分を選択可能にするか
- パスワードの最小・最大文字数と複雑性ルール
- 「土日祝日を含めない」の祝日判定方法

該当機能を実装する直前にユーザーへ確認する。

## 12. 現在の実装順序

当面は以下の順序を優先する。

1. `KenshuMember` EntityとRepository
2. メンバー登録・一覧・更新・削除API
3. パスワードハッシュ化
4. ログイン・ログアウトAPI
5. 認証・認可
6. Reactのメンバー管理とログインをAPI接続へ置換
7. スケジュールAPI
8. ホームAPI
9. 進捗・課題管理API
10. 統合テストと設計書への反映
