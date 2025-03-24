package mathutil

import (
	"testing"
)

// Test cases for the math functions
func TestAdd(t *testing.T) {
	if result := Add(2, 3); result != 5 {
		t.Errorf("expected 5, got %d", result)
	}
}

func TestSubtract(t *testing.T) {
	if result := Subtract(5, 3); result != 2 {
		t.Errorf("expected 2, got %d", result)
	}
}

func TestMultiply(t *testing.T) {
	if result := Multiply(2, 3); result != 6 {
		t.Errorf("expected 6, got %d", result)
	}
}

func TestDivide(t *testing.T) {
	if result, err := Divide(6, 3); err != nil || result != 2 {
		t.Errorf("expected 2, got %d, error: %v", result, err)
	}
}

func TestDivideByZero(t *testing.T) {
	if _, err := Divide(6, 0); err == nil {
		t.Error("expected error, got nil")
	}
}
