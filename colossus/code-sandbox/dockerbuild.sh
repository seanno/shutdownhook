#/bin/sh -c
docker build -t code-sandbox .
docker run --rm code-sandbox pip freeze > code_sandbox_pips.txt
