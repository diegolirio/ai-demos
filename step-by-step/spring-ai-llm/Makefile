
run:
	set -a && source local.env && set +a && ./gradlew bootRun

talk:
	curl -s -X POST localhost:8080/chat -H 'Content-Type: application/json' -d '{"conversationId":"b","message":"Quanto é 2+2?"}'