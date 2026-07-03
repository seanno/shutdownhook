
# PROJECTS

The project space is a file system hierarchy. Each directory represents a project which can run its own conversation and / or contain child projects that inherit some configuration. "Learnings" percolate up and down the hierarchy as appropriate for the given context. Typically conversations will happen at the leaves, but that is not a requirement.

## Project Components

- **config.json** maps to com.shutdownhook.colossus.Projects.Config
- **scripts** contains scripts referenced by pre/post commands
- **temp** is available to scripts and conversations; cleared on every run
- **data** holds long-lived data and history
- **children** if present, contains optional additional project folders
- **systemprompt.md** if present, appended to system prompt for conversation
- **prompt.md** if present, contains the project to be used in conversation at this level
- **learnings.json** contains useful "lessons" identified and persisted by the LLM 
- **conversations** is an archive for conversation histories; not presented to the LLM in future runs

## Project Execution

Inputs: Parent conversation config; project directory; combined parent learnings

0. On creation, setup conversation config
	1. Start from config.json conversation config if present, else parent config
	2. Append systemprompt.md if present
	3. Remove any file tools and add for data/temp

1. Clear temp
2. Run prescript
3. If prompt.md exists
    1. Ensure data and temp directories (doing here prevents useless dirs being created)
	2. Instantiate and run conversation
	3. Archive conversation text
4. If children exist
	1. for each child, runProject passing our conversation config
	2. Consolidate learning (TBD)
5. Run postscript
6. Clear temp


    


