# Item persistent data

Namespace is the Cooking plugin. Keys are declared in `src/main/java/net/tfminecraft/cooking/utils/Keys.java`.

| Key | Stores |
| --- | --- |
| `food_id` | type id |
| `food_category` | category |
| `food_origin` | origin |
| `food_quality` | star |
| `food_base` | overridden food amount |
| `food_nutrition_base` | overridden nutrition level |
| `food_state` | extra state string |
| `food_model` | model id |
| `tags` | serialized tracks |
| `age_remainder` | fractional age |
| `last_update` | last aging timestamp |
| `lore_index` | lore lines owned by the plugin |
| `sauce` | linked sauce item data |
| `sauce_name` | sauce display name |
| `carve_sequence` | sequence id |
| `carve_next_index` | next cut |
| `carve_remaining` | cuts left including non-food |
| `slot_data` | encoded station slots on a scooped item |
| `food_ingredients` | short name ingredient list |
| `food_lineage` | versioned main and extra origins |
| `cook_method` | active cook method |
| `cook_time` | cook progress |
| `catch_size_cm` | rounded catch length in centimetres |
| `seafood_cut_type` | `fish`, `octopus`, or `jellyfish` |
| `custom_fishing_id` | CustomFishing loot id |

Player PDC, same Cooking namespace:

| Key | Stores |
| --- | --- |
| `variety_history` | rolling eaten meals for nutrition variety |

`food_base` and `food_nutrition_base` are removed when the item has no base override.

Entity PDC used by husbandry is separate and listed in [../systems/husbandry.md](../systems/husbandry.md).
