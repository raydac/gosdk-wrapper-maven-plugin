package main

import (
	"testing"

	"google.golang.org/protobuf/proto"
	"github.com/protocolbuffers/protobuf/examples/go/gen"
)

func TestPersonSerialization(t *testing.T) {
	original := &gen.Person{
		Name: "Bob",
		Age:  25,
	}

	// Serialize
	data, err := proto.Marshal(original)
	if err != nil {
		t.Fatalf("Failed to serialize: %v", err)
	}

	// Deserialize
	var deserialized gen.Person
	if err := proto.Unmarshal(data, &deserialized); err != nil {
		t.Fatalf("Failed to deserialize: %v", err)
	}

	// Compare
	if original.Name != deserialized.Name || original.Age != deserialized.Age {
		t.Errorf("Expected %v but got %v", original, deserialized)
	}
}
