# Changelog

All notable changes to this project are documented in this file.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Shops opened by clicking a [FancyNpcs](https://modrinth.com/plugin/fancynpcs) NPC (several NPCs per shop, one shop per NPC).
- Support for modded items on hybrid servers (Youer): items are stored whole, with all their tags and states.
- Optional per-shop access permission (`/us restrict`), registered dynamically so permission plugins can complete it.
- Paginated shop menu, purchase confirmation screen, protection against inventory duplication tricks.
- Purchases through Vault with decimal prices, refund on failure, and an inventory space check before charging.
- History of the latest purchases (`/us history`) and an optional full purchase log file.
- `/us validate` to detect unreadable items and missing NPCs.
- Public API (`UsefullShopsAPI`) and events (`ShopOpenEvent`, `ShopPurchaseEvent`, `ShopPurchasedEvent`).
- French and English messages.
