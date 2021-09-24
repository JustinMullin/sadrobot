create table authenticated_workspaces
(
  id INTEGER
    primary key autoincrement,
  enabled int,
  workspaceType text,
  workspaceId text,
  name text,
  token text,
  botToken text,
  botUserId text,
  allowSingleDelimiter int
);

create unique index authenticated_workspaces_workspaceType_workspaceId_uindex
  on authenticated_workspaces (workspaceType, workspaceId);

