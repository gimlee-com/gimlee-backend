#!/bin/bash

# Default MongoDB connection details
DEFAULT_HOST="localhost"
DEFAULT_PORT="27017"
DEFAULT_DB="gimlee"
DEFAULT_AUTH_DB="admin"

# Initialize variables with default values
HOST=$DEFAULT_HOST
PORT=$DEFAULT_PORT
DB=$DEFAULT_DB
AUTH_DB=$DEFAULT_AUTH_DB
COLLECTION=""
JSON_FILE=""
USER=""
PASS=""

usage() {
  echo "Usage: $0 -f <json_file_path> [-h <host>] [-p <port>] [-d <database>] [-c <collection>] [-u <user>] [-P <password>] [-A <auth_db>]"
  echo ""
  echo "Options:"
  echo "  -f <json_file_path>   Path to the JSON file to import (required)."
  echo "  -h <host>             MongoDB host (default: $DEFAULT_HOST)."
  echo "  -p <port>             MongoDB port (default: $DEFAULT_PORT)."
  echo "  -d <database>         MongoDB database name (default: $DEFAULT_DB)."
  echo "  -c <collection>       MongoDB collection name (required)."
  echo "  -u <user>             MongoDB username (optional)."
  echo "  -P <password>         MongoDB password (optional)."
  echo "  -A <auth_db>          MongoDB authentication database (default: $DEFAULT_AUTH_DB)."
  echo ""
  echo "Example: $0 -f ./mydata.json -d mydatabase -c mycollection -u admin -P password"
  exit 1
}

while getopts ":h:p:d:c:f:u:P:A:" opt; do
  case ${opt} in
    h )
      HOST=$OPTARG
      ;;
    p )
      PORT=$OPTARG
      ;;
    d )
      DB=$OPTARG
      ;;
    c )
      COLLECTION=$OPTARG
      ;;
    f )
      JSON_FILE=$OPTARG
      ;;
    u )
      USER=$OPTARG
      ;;
    P )
      PASS=$OPTARG
      ;;
    A )
      AUTH_DB=$OPTARG
      ;;
    \? )
      echo "Invalid option: $OPTARG" 1>&2
      usage
      ;;
    : )
      echo "Invalid option: $OPTARG requires an argument" 1>&2
      usage
      ;;
  esac
done
shift $((OPTIND -1))

if [ -z "$JSON_FILE" ]; then
  echo "Error: JSON file path is required."
  usage
fi

if [ ! -f "$JSON_FILE" ]; then
  echo "Error: File '$JSON_FILE' not found."
  exit 1
fi

MONGOIMPORT_CMD="mongoimport --host ${HOST} --port ${PORT} --db ${DB} --collection ${COLLECTION} --file ${JSON_FILE} --jsonArray"

if [ -n "$USER" ]; then
  MONGOIMPORT_CMD="${MONGOIMPORT_CMD} --username ${USER} --password ${PASS} --authenticationDatabase ${AUTH_DB}"
fi

echo "Importing '$JSON_FILE' into MongoDB:"
echo "  Host:       $HOST"
echo "  Port:       $PORT"
echo "  Database:   $DB"
echo "  Collection: $COLLECTION"
echo ""
echo "Executing command:"
echo "$MONGOIMPORT_CMD"
echo ""

eval "$MONGOIMPORT_CMD"

if [ $? -eq 0 ]; then
  echo ""
  echo "Successfully imported '$JSON_FILE' to collection '$COLLECTION' in database '$DB'."
else
  echo ""
  echo "Error during import. Please check the output above for details."
  exit 1
fi

exit 0