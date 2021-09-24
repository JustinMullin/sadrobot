#!/bin/bash

printf "Hello! Here are the current active workspaces for Sad Robot.\n\n"
sqlite3 Tokens.sqlite 'select id,name from authenticated_workspaces where enabled=1;'
