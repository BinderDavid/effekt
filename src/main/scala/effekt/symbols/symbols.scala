package effekt

import effekt.source.{ Def, ValDef, VarDef, FunDef }

/**
 * The symbol table contains things that can be pointed to:
 * - function definitions
 * - type definitions
 * - effect definitions
 * - parameters
 * - value / variable binders
 * - ...
 */
package object symbols {

  // reflecting the two name spaces
  sealed trait TypeSymbol extends Symbol
  sealed trait TermSymbol extends Symbol

  // the two universes of values and blocks
  sealed trait ValueSymbol extends TermSymbol
  sealed trait BlockSymbol extends TermSymbol

  def moduleName(path: String) = "$" + path.replace('/', '_')
  def moduleFile(path: String) = path.replace('/', '_') + ".js"

  sealed trait Param extends TermSymbol
  case class ValueParam(name: Name, tpe: Option[ValueType]) extends Param with ValueSymbol
  case class BlockParam(name: Name, tpe: BlockType) extends Param with BlockSymbol
  case class ResumeParam() extends Param with BlockSymbol { val name = LocalName("resume") }
  

  /**
   * Right now, parameters are a union type of a list of value params and one block param.
   */
  // TODO Introduce ParamSection also on symbol level and then use Params for types
  type Params = List[List[ValueParam] | BlockParam]

  def paramsToTypes(ps: Params): Sections =
    ps map {
      case BlockParam(_, tpe) => tpe
      case vps: List[ValueParam] => vps map { v => v.tpe.get }
    }


  trait Fun extends BlockSymbol {
    def tparams: List[TypeVar]
    def params: Params
    def ret: Option[Effectful]

    // invariant: only works if ret is defined!
    def toType: BlockType = BlockType(tparams, paramsToTypes(params), ret.get)
    def toType(ret: Effectful): BlockType = BlockType(tparams, paramsToTypes(params), ret)
  }

  object Fun {
    def unapply(f: Fun): Option[(Name, List[TypeVar], Params, Option[Effectful])] = Some((f.name, f.tparams, f.params, f.ret))
  }

  case class UserFunction(
    name: Name,
    tparams: List[TypeVar],
    params: Params,
    ret: Option[Effectful],
    decl: FunDef) extends Fun

  /**
   * Binders represent local value and variable binders
   *
   * They also store a reference to the original defition in the source code
   */
  sealed trait Binder extends ValueSymbol {
    def tpe: Option[ValueType]
    def decl: Def
  }
  case class ValBinder(name: Name, tpe: Option[ValueType], decl: ValDef) extends Binder
  case class VarBinder(name: Name, tpe: Option[ValueType], decl: VarDef) extends Binder


  /**
   * Types
   */

  sealed trait Type

  // like Params but without name binders
  type Sections = List[List[ValueType] | BlockType]

  sealed trait ValueType extends Type

  case class TypeVar(name: Name) extends ValueType with TypeSymbol
  case class TypeApp(tpe: DataType, args: List[ValueType]) extends ValueType

  case class BlockType(tparams: List[TypeVar], params: Sections, ret: Effectful) extends Type

  case class DataType(name: Name, tparams: List[TypeVar], var ctors: List[Constructor] = Nil) extends ValueType with TypeSymbol
  case class Constructor(name: Name, params: List[List[ValueParam]], datatype: DataType) extends Fun {
    def tparams = datatype.tparams
    def ret = if (tparams.size > 0) Some(Effectful(TypeApp(datatype, tparams), Pure)) else Some(Effectful(datatype, Pure))
  }

  sealed trait Effect extends TypeSymbol
  case class UserEffect(name: Name, tparams: List[TypeVar], var ops: List[EffectOp] = Nil) extends Effect
  case class EffectOp(name: Name, tparams: List[TypeVar], params: List[List[ValueParam]], ret: Option[Effectful], effect: UserEffect) extends Fun

  /**
   * symbols.Effects is like source.Effects, but with resolved effects
   *
   * Effect sets and effectful computations are themselves *not* symbols, they are just aggregates
   */
  case class Effects(effs: List[Effect]) {
    def +(eff: Effect): Effects = Effects(eff :: effs).distinct
    def -(eff: Effect): Effects =  Effects((effs.toSet - eff).toList).distinct
    def ++(other: Effects): Effects = Effects(effs ++ other.effs).distinct
    def --(other: Effects): Effects = Effects((effs.toSet -- other.effs.toSet).toList)

    def isEmpty: Boolean = effs.isEmpty
    def nonEmpty: Boolean = effs.nonEmpty

    def distinct = Effects(effs.distinct)

    def contains(e: Effect): Boolean = effs.contains(e)

    override def toString: String = s"{${effs.mkString(", ")}}"
  }

  object / {
    def unapply(e: Effectful): Option[(ValueType, Effects)] = Some(e.tpe, e.effects)
  }
  val Pure = Effects(Nil)
  case class Effectful(tpe: ValueType, effects: Effects) // TODO change tpe to ValueType


  /**
   * Builtins
   */
  sealed trait Builtin extends Symbol {
    override def builtin = true
  }

  case class BuiltinFunction(name: Name, tparams: List[TypeVar], params: Params, ret: Option[Effectful], pure: Boolean = true, body: String = "") extends Fun with Builtin
  case class BuiltinType(name: Name, tparams: List[TypeVar]) extends ValueType with TypeSymbol with Builtin
  case class BuiltinEffect(name: Name, tparams: List[TypeVar] = Nil) extends Effect with TypeSymbol with Builtin

  def isBuiltin(e: Symbol): Boolean = e.builtin
}
