#!/bin/bash
PATH_HERE=
curl --request POST \
    --url http://localhost:8080/ \
    --header 'content-type: application/json' \
    --data "{
      \"pathToMsRoots\": [\"${PATH_HERE}\"]
  }" > output.json

