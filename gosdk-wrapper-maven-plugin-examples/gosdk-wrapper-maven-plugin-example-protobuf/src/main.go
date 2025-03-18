package main

import (
	"encoding/json"
	"fmt"
	"log"

	"google.golang.org/protobuf/proto"
	"github.com/protocolbuffers/protobuf/examples/go/gen"
)

func main() {
	p := &gen.Person{
		Name: "Alice",
		Age:  30,
	}

	// Serialize to Protobuf
	data, err := proto.Marshal(p)
	if err != nil {
		log.Fatalf("Failed to serialize: %v", err)
	}

	// Deserialize from Protobuf
	var person gen.Person
	if err := proto.Unmarshal(data, &person); err != nil {
		log.Fatalf("Failed to deserialize: %v", err)
	}

	// Print result
	jsonData, _ := json.MarshalIndent(person, "", "  ")
	fmt.Println(string(jsonData))
}