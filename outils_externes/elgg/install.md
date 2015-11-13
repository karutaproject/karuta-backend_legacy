# SETTINGS Elgg 1.12.x for Karuta 1.2

## Dependencies:

* Elgg 1.12 installed on a web server

## Settings steps:

1. Download elgg plugin "TheWire Tools 4" [https://elgg.org/plugins/download/2239772](https://elgg.org/plugins/download/2239772)

2. Download elgg plugin "Group Tools 5"
[https://elgg.org/plugins/download/2239739](https://elgg.org/plugins/download/2239739)

3. Unzip and upload in your elgg/mod folder these two plugins

4. Go to Elgg administration > Configure > Settings > Plugins

	1. Reorder the plugins priority where "TheWire Tools 4" must be before "Group Tools 5"
	2. Activate "TheWire Tools 4” plugin and "Group Tools 5" plugin
	3. Activate "Web Services" plugin

5. Go to Elgg administration > Configure > Settings

	1. For TheWire Tools : select « Yes » for all the values and @displayname for the last value
	2. For « Group » : select « Yes » for allowing private (invisible) groups  

## Optionals:

### Adding sub-groups functionalities

1. Download and install elgg plugin "AU Sub-Groups 2.1.0"
[https://elgg.org/plugins/download/2366751](https://elgg.org/plugins/download/2366751)
2. Reorder the plugins priority where "AU Sub-Groups 2.1.0" must be after "Group Tools 5”
3. Activate elgg plugin "AU Sub-Groups 2.1.0"

### Adding french languages (3 files) if you set up in the admin and in the user front-end the elgg site in french

1. Download TheWire Tools 4 [french language file](/outils_externes/elgg/mod/thewire_tools/languages/fr.php "/outils_externes/elgg/mod/thewire_tools/languages/fr.php") and upload it on the folder 
 mod > thewire_tools > Languages >
2. Download Group Tools  5 [french language file](/outils_externes/elgg/mod/group_tools/languages/fr.php "/outils_externes/elgg/mod/group_tools/languages/fr.php") and upload it on the folder 
 mod > group_tools > Languages>
3. Download AU Subgroups 2.1.0 [french language file](/outils_externes/elgg/mod/au_subgroups/languages/fr.php "/outils_externes/elgg/mod/au_subgroups/languages/fr.php") and upload it on the folder 
 mod > au_subgroups > Languages>
