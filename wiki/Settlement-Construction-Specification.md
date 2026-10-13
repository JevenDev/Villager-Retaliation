# Settlement construction

Settlements build complete authored structure templates using queued projects, reserved materials and dedicated builders. Construction uses existing territory ownership and delegated BUILD, STAFF and LOGISTICS permissions.

## Construction flow

1. Select House, then Small, Medium or Large, and a previewed variant.
2. Position and rotate the world blueprint. The server checks territorial permissions, height, loaded terrain, protected regions, containers, obstructions and project overlap.
3. Submit a pending project. Pending projects can be reordered by dragging, with each reorder checked against current state.
4. Eligible dedicated builders automatically take queued work. Active projects cannot be reordered or manually paused. Cancel stops a project. Missing workers, materials, tools, paths or safe terrain cause visible waits that retain progress.
5. The complete building quote is reserved before construction. Site preparation tools, tool wear and support or fill are additional requirements.
6. Builders physically collect authorized supplies and build. Couriers transport between authorized stores. Manual completion of a blueprint by a player is not credited as construction work.
7. Cancellation leaves placed blocks and refunds unspent payment. Refunds that cannot be stored remain pending.

## Templates and costs

Each registration references a complete Minecraft template. Costs support explicit items, material tags and an exact-material mode. Simplified payment does not recolor or substitute authored blocks. Prices are chosen by the pack author.

House category and size metadata provide variant browsing. Foundation, porch, furniture and palette belong in the exported template. Guide grids do not assemble modular structures automatically. Roads, upgrades, tier reclassification and housing comfort are outside this construction system.

Exact requirements account for doors, beds and other multiblock units, stacked block states, fluids, waterlogging and air clearance. Unsupported output rejects the template instead of becoming free. Template entities, loot tables and stored valuables cannot supply free contents. See the [settlement house example](../example-packs/settlement-houses/README.md) for registration syntax and supported block handling.

Simplified spending follows a deterministic work schedule. Cumulative rounding totals the quote on completion and prevents valuable early output without corresponding payment. Submitted stacks retain their components and provenance for refunds, including tag alternatives.

## Ownership, recovery and refunds

Every unit has one spendable owner at a time: source storage, reserved project escrow, spent payment or refund escrow. Physical collection does not create a second spendable worker-inventory copy. The whole quote is checked before inventory changes. Repeated commands and reloads cannot reserve, spend, place or refund the same unit twice.

World and inventory changes use settlement receipts and checkpoints. Chunk containers and settlement saved data have separate save lifecycles, so main-thread execution alone does not guarantee recovery from power loss.

Cancellation first tries the original source, then eligible settlement fallback storage in a deterministic order. Remaining exact stacks persist with the status **Refund awaiting storage** and retry when capacity and access return. Spent preparation materials and tool durability are not refunded.

## Storage and site safety

Default shared storage is available to permitted settlement work. Role filters support builder-only storage and general access, with explicit transport access for couriers. Item filters, ownership checks, storage locks and block permissions still apply.

The preview includes natural obstacles such as terrain and trees. Preparation consumes appropriate tools and support, and waits for missing requirements. Manual clearing can unblock a project. Filled containers and protected builds require player intervention. Protection is conservative because block-placement history is not always available.

## Hand-built housing

A selected housing area needs at least a 3 by 3 footprint, a solid floor, a functioning door and usable entrance, reachable beds, sufficient clearance and an accepted light source. Lanterns are accepted. Each usable bed supplies one slot and is counted once across areas. Walls and roofs are not required.
