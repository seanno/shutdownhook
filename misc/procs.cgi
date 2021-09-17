#!/bin/bash
export TBL=$( ps --sort "-%cpu" -ax -o "%C" -o "	%t" -o "	%p" -o "	%u" -o "	%a" )
java -cp toolbox-1.0-SNAPSHOT.jar com.shutdownhook.toolbox.Template tsv_to_table.html.tmpl


