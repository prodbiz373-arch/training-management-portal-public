# Third-party notices

## shadcn/ui

The following files include code derived from shadcn/ui:

- `frontend/components/ui/button.tsx` — Base UI Button component, adapted for this project.
- `frontend/lib/utils.ts` — `cn` utility pattern.

Source project: [shadcn/ui](https://github.com/shadcn-ui/ui).
The project configuration identifies the `base-nova` style. The exact upstream revision used for the original import has not been established.

Copyright (c) 2023 shadcn

These portions are licensed under the MIT License. The complete copyright and permission notice is preserved in [licenses/shadcn-ui-MIT.txt](licenses/shadcn-ui-MIT.txt).

This notice applies to the identified third-party portions. It does not license this entire project, its design documents, or other assets under MIT.

## Dependencies and fonts

Dependency versions are recorded in [frontend/pnpm-lock.yaml](frontend/pnpm-lock.yaml) and [pom.xml](pom.xml). These packages retain their respective licenses. This source repository does not include installed dependency directories, font binaries, or compiled application distributions.

This document is not a complete license inventory for a future bundled distribution. Redistribution of dependency code, browser bundles, JARs, native binaries, containers, or font files requires preserving applicable license and attribution notices and meeting the relevant source-availability requirements.


## Icons visible in the application and captured screens

The graduation-cap header symbol and navigation/action icons are from `lucide-react`. They are generic UI icons, not the v0 or Vercel brand logo. The current application favicon was independently drawn for this project and is separate from the header symbol.

Lucide is distributed under the ISC License. Icons inherited from Feather have the MIT notice included in the package license. The complete, unmodified license text from the installed `lucide-react` package is preserved in [licenses/lucide-ISC-and-Feather-MIT.txt](licenses/lucide-ISC-and-Feather-MIT.txt), including both copyright notices. See the [official Lucide license](https://lucide.dev/license).

This notice also identifies icons visible in the design document screenshots. Keep the applicable third-party notices with copies of those design materials; it does not relicense the entire workbook or prove rights to all v0 inputs.

## Fonts visible in captured screens

The frontend declares Noto Sans JP and Geist Mono through `next/font/google`. Their upstream licenses are SIL Open Font License 1.1: [Noto Sans JP](https://github.com/google/fonts/blob/main/ofl/notosansjp/OFL.txt), [Geist](https://github.com/vercel/geist-font/blob/main/LICENSE.txt). The captured text is rasterized; the Excel files do not redistribute these webfont binaries. OFL conditions for redistributing font software differ from publishing graphics made with the fonts; see the [official OFL FAQ](https://openfontlicense.org/ofl-faq/). Actual browser fallback fonts can vary by environment. Future font/bundle redistribution requires checking the files actually shipped.

## shadcn/ui stylesheet retained locally

`frontend/styles/shadcn-tailwind.css` retains the CSS body from `shadcn@4.22.1/dist/tailwind.css`, with a provenance comment. Source: [shadcn/ui](https://github.com/shadcn-ui/ui/tree/main/packages/shadcn). Copyright (c) 2023 shadcn. The complete MIT notice is preserved in [licenses/shadcn-ui-MIT.txt](licenses/shadcn-ui-MIT.txt). This applies to the retained CSS, in addition to the Button/utils portions identified above; it does not license the entire project.

The CLI package is removed from current dependencies. Generated components and `components.json` remain; any future generation workflow requires a separately reviewed CLI version.

## Apache Maven Wrapper 3.3.4

The `mvnw` and `mvnw.cmd` launcher scripts are from Apache Maven Wrapper 3.3.4 (the only-script distribution). Their existing license headers are retained. The scripts match the official templates after substituting the wrapper version; this documentation update does not modify either script.

Source: [Apache Maven Wrapper 3.3.4](https://github.com/apache/maven-wrapper/tree/maven-wrapper-3.3.4).
The complete upstream [LICENSE](licenses/apache-maven-wrapper-3.3.4-LICENSE.txt) and [NOTICE](licenses/apache-maven-wrapper-3.3.4-NOTICE.txt) are included without alteration. Keep these with redistributed copies of the identified scripts. [Source URLs and hashes](docs/verification/wrapper-license-sources.md) record the exact documents obtained.

Apache-2.0 applies to the identified Wrapper material. This addition does not apply Apache-2.0 to this entire project, its application code, design documents, or other assets. Existing MIT, ISC, and font notices above remain in effect for their respective materials. These launcher notices are not a complete license inventory of Maven, downloaded dependencies, generated bundles, or future application distributions.

[Historical distribution notes](docs/verification/historical-distribution-notes.md) describe unresolved notice coverage in previously committed snapshots. Adding this notice does not retroactively change those snapshots.
