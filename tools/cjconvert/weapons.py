"""The weapons: models of gta3.img, each with a .txd of the same name."""

WEAPON_MODELS = ("colt45", "desert_eagle", "chromegun", "micro_uzi", "ak47", "m4", "minigun", "rocketla", "bat", "jetpack")
# Weapons whose model is more than the body on the root frame (the minigun's barrels hang from a child frame): their child atomics are
# assembled into the model. For the others the child atomics are only the muzzle flash.
ASSEMBLED = ("minigun", "jetpack")
# The node of CJ's skeleton a model is attached to, when it is not the right hand: the jetpack rides on the upper torso (Spine1, nodeID 3),
# as in the game (the matrix of index 3 of the ped's hierarchy).
ATTACH_NODES = {"jetpack": 3}
RIGHT_HAND_NODE = 24  # nodeID of CJ's right hand: the weapon's only joint
