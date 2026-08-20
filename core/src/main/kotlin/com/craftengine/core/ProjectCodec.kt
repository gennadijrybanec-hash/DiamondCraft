package com.craftengine.core

/** Stable, dependency-free text format for shared projects. v1 can be imported by every Craft app. */
object ProjectCodec {
    private const val MAGIC = "CRAFTPROJECT|1"
    fun encode(project: CraftProject): String = buildString {
        appendLine(MAGIC)
        appendLine("id=${escape(project.id)}")
        appendLine("name=${escape(project.name)}")
        appendLine("mode=${project.mode.name}")
        appendLine("updatedAt=${project.updatedAt}")
        appendLine("size=${project.grid.width},${project.grid.height}")
        appendLine("palette=${project.grid.palette.joinToString(";") { "${escape(it.id)},${escape(it.name)},${it.argb}" }}")
        append("cells=")
        append(project.grid.cells.joinToString(";") { "${it.colorIndex},${if(it.completed)1 else 0},${if(it.hidden)1 else 0}" })
    }
    fun decode(text: String): CraftProject {
        val lines=text.lineSequence().toList(); require(lines.firstOrNull()==MAGIC) { "Unsupported Craft project" }
        val m=lines.drop(1).associate { it.substringBefore('=') to it.substringAfter('=',"") }
        val (w,h)=m.getValue("size").split(',').map(String::toInt)
        val palette=m.getValue("palette").takeIf{it.isNotEmpty()}?.split(';')?.map { row ->
            val p=splitEscaped(row, ','); CraftColor(unescape(p[0]), unescape(p[1]), p[2].toInt())
        }.orEmpty()
        val cells=m.getValue("cells").takeIf{it.isNotEmpty()}?.split(';')?.map { row ->
            val p=row.split(','); CraftCell(p[0].toInt(),p[1]=="1",p[2]=="1")
        }.orEmpty()
        return CraftProject(unescape(m.getValue("id")),unescape(m.getValue("name")),CraftMode.valueOf(m.getValue("mode")),CraftGrid(w,h,palette,cells),m.getValue("updatedAt").toLong())
    }
    private fun escape(s:String)=s.replace("\\","\\\\").replace(",","\\,").replace(";","\\;").replace("\n","\\n")
    private fun unescape(s:String):String { val out=StringBuilder(); var esc=false; for(c in s){ if(esc){out.append(if(c=='n')'\n' else c);esc=false}else if(c=='\\')esc=true else out.append(c)}; return out.toString() }
    private fun splitEscaped(s:String, delimiter:Char):List<String>{ val out=mutableListOf<String>();val b=StringBuilder();var esc=false;for(c in s){if(esc){b.append('\\').append(c);esc=false}else if(c=='\\')esc=true else if(c==delimiter){out+=b.toString();b.clear()}else b.append(c)};out+=b.toString();return out}
}
