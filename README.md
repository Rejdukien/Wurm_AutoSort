# Wurm AutoSort
Clientside mod, will automatically sort items into containers based on user-defined filters.

### Available console commands:
* **addRule [options] item -> [options] container**: Adds a new sorting rule
* **listRules**: Lists all available sorting rules
* **delRule item**: Removes sorting rule for item
* **sortInventory**: Sorts inventory based on rules
* **printInventory**: More of a debug command, will print the inventory tree in console

### Available options:
* **world**: Will check other inventories for that item/container rather than your own inventory
* **__number__**: Quantity to keep in target container. No effect on source item.

##### Examples
* ```addRule corn -> [world|1] frying pan``` - Will keep 1 corn (from a player inventory) in each frying pan in a non-player inventory (eg. Oven/Forge etc.)
* ```addRule arrow head -> pottery bowl``` - Move any arrow heads in your inventory into a pottery bowl in your inventory
* ```addRule dragon scale -> (Armor Set)``` - Move any item containing "dragon scale" in its name into a container labeled (Armor Set) within your inventory.
* ```delRule corn``` - Removes the first rule regarding sorting of corn.