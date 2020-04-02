package effekt
package context

import effekt.source.{ Id, IdDef }
import effekt.symbols.{ Symbol, Module }
import org.bitbucket.inkytonik.kiama.util.Memoiser

/**
 * The *global* symbol database (across modules)
 */
trait SymbolsDB { self: Context =>

  private val symbols: Memoiser[Id, Symbol] = Memoiser.makeIdMemoiser

  // for reverse lookup in LSP server
  private val sources: Memoiser[Symbol, IdDef] = Memoiser.makeIdMemoiser

  // the module a symbol is defined in
  private val modules: Memoiser[Symbol, Module] = Memoiser.makeIdMemoiser

  def assignSymbol(id: Id, d: Symbol): Unit = id match {
    case id: IdDef =>
      sources.put(d, id)
      symbols.put(id, d)
      modules.put(d, module)
    case _ =>
      symbols.put(id, d)
      modules.put(d, module)
  }

  def symbolOf(id: Id): Symbol = symbolOption(id) getOrElse {
    abort(s"Internal Compiler Error: Cannot find symbol for ${id}")
  }
  def symbolOption(id: Id): Option[Symbol] = symbols.get(id)

  def owner(sym: Symbol): Module = modules(sym)

  // Searching the defitions for a Reference
  // =======================================
  // this one can fail.
  def symbolOf(tree: source.Reference): tree.symbol =
    symbolOf(tree.id).asInstanceOf[tree.symbol]

  // Searching the symbol for a definition
  // =====================================
  // these lookups should not fail (except there is a bug in the compiler)
  def symbolOf(tree: source.Definition): tree.symbol =
    symbolOf(tree.id).asInstanceOf[tree.symbol]

  // Searching the definition for a symbol
  // =====================================
  def definitionTreeOf(s: Symbol): Option[IdDef] = sources.get(s)
}