package hello

import "testing"

// TestHelloWorld checks if HelloWorld function returns the expected string
func TestHelloWorld(t *testing.T) {
    got := HelloWorld()
    want := "Hello, World!"

    if got != want {
        t.Errorf("HelloWorld() = %v, want %v", got, want)
    }
}