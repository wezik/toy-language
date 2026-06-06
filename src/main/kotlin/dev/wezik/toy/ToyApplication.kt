package dev.wezik.toy

import java.io.File

fun main(args: Array<String>) {
	val filePath = if (args.isNotEmpty()) args[0] else error("need file path")
	val bytes = File(filePath).readBytes()
	println(bytes.toString(Charsets.UTF_8))
}
