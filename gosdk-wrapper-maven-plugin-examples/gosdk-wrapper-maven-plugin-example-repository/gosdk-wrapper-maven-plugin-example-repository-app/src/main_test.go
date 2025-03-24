package main

import (
	"testing"
	mathutil "github.com/raydac/gosdk-wrapper-maven-plugin/repository/lib/mathutil"
)

func TestMainFunctions(t *testing.T) {
	if result := mathutil.Add(3, 7); result != 10 {
		t.Errorf("expected 10, got %d", result)
	}

	if result := mathutil.Subtract(10, 4); result != 6 {
		t.Errorf("expected 6, got %d", result)
	}

	if result := mathutil.Multiply(3, 3); result != 9 {
		t.Errorf("expected 9, got %d", result)
	}

	if result, err := mathutil.Divide(10, 2); err != nil || result != 5 {
		t.Errorf("expected 5, got %d, error: %v", result, err)
	}

	if _, err := mathutil.Divide(10, 0); err == nil {
		t.Error("expected error, got nil")
	}
}