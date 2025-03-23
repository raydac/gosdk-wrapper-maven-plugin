//#excludeif true
//#-
package main

import (
	"bytes"
	"fmt"
	"image/png"
	"log"
)

func main() {
	//#+
	var imageArray = []uint8{/*$binfile("../res/image.png","uint8[]s")$*/}
	var imageConfig, errDecode = png.DecodeConfig(bytes.NewBuffer(imageArray))
	if errDecode != nil {
		log.Fatal(errDecode)
	}

	fmt.Printf("The ibjected image has size %dx%d\n", imageConfig.Width, imageConfig.Height)

	//#local text=str2java(evalfile("../res/text.txt"),false)
	fmt.Println( /*$"\""+text+"\""$*/ )
	//#-
}

//#+
