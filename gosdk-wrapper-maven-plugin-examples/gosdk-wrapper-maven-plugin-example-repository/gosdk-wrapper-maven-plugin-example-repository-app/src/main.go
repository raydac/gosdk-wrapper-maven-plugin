package main

import (
	"fmt"
	mathutil "github.com/raydac/gosdk-wrapper-maven-plugin/repository/lib/mathutil"
)

func main() {
	fmt.Println("Addition: ", mathutil.Add(10, 5))
	fmt.Println("Subtraction: ", mathutil.Subtract(10, 5))
	fmt.Println("Multiplication: ", mathutil.Multiply(10, 5))
	result, err := mathutil.Divide(10, 5)
	if err != nil {
		fmt.Println("Error: ", err)
	} else {
		fmt.Println("Division: ", result)
	}
}