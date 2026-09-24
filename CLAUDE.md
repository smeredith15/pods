@AGENTS.md

# Fork notes (SHUFFLEPOD)
This checkout is a personal fork of AntennaPod, displayed on the phone as "Podcasts".
The plan, decisions and roadmap live in `docs/SHUFFLEPOD.md`; read it before working on fork features.
These rules take precedence over the upstream instructions above where they conflict:
- Mark every change to an existing upstream file with a `// SHUFFLEPOD:` comment (or `<!-- SHUFFLEPOD -->` in XML) so upstream merges are easy to resolve.
- Put fork features in new files/modules; keep edits to upstream files to single hook calls where possible.
- Fork data goes in its own database, never in AntennaPod's `PodDBAdapter` schema.
- Builds run on GitHub Actions (`.github/workflows/shufflepod-build.yml`); the free flavor is the one we ship.
- Do not open PRs against AntennaPod/AntennaPod, and ignore the upstream PR/issue conventions above.
