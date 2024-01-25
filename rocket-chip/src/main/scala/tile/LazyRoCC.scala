// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

package freechips.rocketchip.tile

import Chisel._

import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.InOrderArbiter

import chisel3.core.{Input, Output}

case object BuildRoCC extends Field[Seq[Parameters => LazyRoCC]](Nil)

//flang
class DFIPort(implicit p: Parameters) extends CoreBundle {
	val test = Bits(width = xLen)
	val taraddr = Bits(width = xLen)
	val ltaraddr = Bits(width = xLen)
	val staraddr = Bits(width = xLen)
	val cmd = Bits(width = xLen)
	val valid = Bool(INPUT)
	val svalid = Bool(INPUT)
	val lvalid = Bool(INPUT)
	val priv = Bits(width = xLen)
	val cycle = Bits(width = xLen)
	val cycle_now = Bits(width = xLen)
	val threadptr = Input(UInt(xLen.W))
	val funcretaddr = Input(UInt(xLen.W))
	val funcstackaddr = Input(UInt(xLen.W))
	val funcarg0 = Input(UInt(xLen.W))
	val funcarg1 = Input(UInt(xLen.W))
	val funcarg2 = Input(UInt(xLen.W))
	val funcarg3 = Input(UInt(xLen.W))
	val funcarg4 = Input(UInt(xLen.W))
	val funcarg5 = Input(UInt(xLen.W))
	val callvalid = Bool(INPUT)
	val retvalid = Bool(INPUT)
	val brtarget = Input(SInt(xLen.W))
}

class RoCCInstruction extends Bundle {
  val funct = Bits(width = 7)
  val rs2 = Bits(width = 5)
  val rs1 = Bits(width = 5)
  val xd = Bool()
  val xs1 = Bool()
  val xs2 = Bool()
  val rd = Bits(width = 5)
  val opcode = Bits(width = 7)
}

class RoCCCommand(implicit p: Parameters) extends CoreBundle()(p) {
  val inst = new RoCCInstruction
  val rs1 = Bits(width = xLen)
  val rs2 = Bits(width = xLen)
  val status = new MStatus
}

class RoCCResponse(implicit p: Parameters) extends CoreBundle()(p) {
  val rd = Bits(width = 5)
  val data = Bits(width = xLen)
}

class RoCCCoreIO(implicit p: Parameters) extends CoreBundle()(p) {
  val cmd = Decoupled(new RoCCCommand).flip
  val resp = Decoupled(new RoCCResponse)
  val mem = new HellaCacheIO
  val busy = Bool(OUTPUT)
  val interrupt = Bool(OUTPUT)
  val exception = Bool(INPUT)
  //flang
  val corestalled = Bool(INPUT)
  val corestall = Bool(OUTPUT)
  val coreexception = Bool(OUTPUT)
  val canexception = Bool(INPUT)
  val cmdvalid = Bool(INPUT)
  val test = Bits(width = xLen).flip
  val dfi = Decoupled(new DFIPort).flip
}

class RoCCIO(val nPTWPorts: Int)(implicit p: Parameters) extends RoCCCoreIO()(p) {
  val ptw = Vec(nPTWPorts, new TLBPTWIO)
  val fpu_req = Decoupled(new FPInput)
  val fpu_resp = Decoupled(new FPResult).flip
}

/** Base classes for Diplomatic TL2 RoCC units **/
abstract class LazyRoCC(
      val opcodes: OpcodeSet,
      val nPTWPorts: Int = 0,
      val usesFPU: Boolean = false
    )(implicit p: Parameters) extends LazyModule {
  val module: LazyRoCCModuleImp
  val atlNode: TLNode = TLIdentityNode()
  val tlNode: TLNode = TLIdentityNode()
}

class LazyRoCCModuleImp(outer: LazyRoCC) extends LazyModuleImp(outer) {
  val io = IO(new RoCCIO(outer.nPTWPorts))
}

/** Mixins for including RoCC **/

trait HasLazyRoCC extends CanHavePTW { this: BaseTile =>
  val roccs = p(BuildRoCC).map(_(p))

  roccs.map(_.atlNode).foreach { atl => tlMasterXbar.node :=* atl }
  roccs.map(_.tlNode).foreach { tl => tlOtherMastersNode :=* tl }

  nPTWPorts += roccs.map(_.nPTWPorts).foldLeft(0)(_ + _)
  nDCachePorts += roccs.size
}

trait HasLazyRoCCModule extends CanHavePTWModule
    with HasCoreParameters { this: RocketTileModuleImp with HasFpuOpt =>

  val (respArb, cmdRouter) = if(outer.roccs.size > 0) {
    val respArb = Module(new RRArbiter(new RoCCResponse()(outer.p), outer.roccs.size))
    val cmdRouter = Module(new RoccCommandRouter(outer.roccs.map(_.opcodes))(outer.p))
    outer.roccs.zipWithIndex.foreach { case (rocc, i) =>
      ptwPorts ++= rocc.module.io.ptw
      rocc.module.io.cmd <> cmdRouter.io.out(i)
      val dcIF = Module(new SimpleHellaCacheIF()(outer.p))
      dcIF.io.requestor <> rocc.module.io.mem
      dcachePorts += dcIF.io.cache
      respArb.io.in(i) <> Queue(rocc.module.io.resp)
    }

    fpuOpt foreach { fpu =>
      val nFPUPorts = outer.roccs.filter(_.usesFPU).size
      if (usingFPU && nFPUPorts > 0) {
        val fpArb = Module(new InOrderArbiter(new FPInput()(outer.p), new FPResult()(outer.p), nFPUPorts))
        val fp_rocc_ios = outer.roccs.filter(_.usesFPU).map(_.module.io)
        fpArb.io.in_req <> fp_rocc_ios.map(_.fpu_req)
        fp_rocc_ios.zip(fpArb.io.in_resp).foreach {
          case (rocc, arb) => rocc.fpu_resp <> arb
        }
        fpu.io.cp_req <> fpArb.io.out_req
        fpArb.io.out_resp <> fpu.io.cp_resp
      } else {
        fpu.io.cp_req.valid := Bool(false)
        fpu.io.cp_resp.ready := Bool(false)
      }
    }
    (Some(respArb), Some(cmdRouter))
  } else {
    (None, None)
  }
}

//flang++++++++++++++++

class DFIFIFO(depth: Int, datawidth: Int)(implicit p: Parameters) extends Module {
	val io = new Bundle{
		val rst = Bool(INPUT)
		val write = Bool(INPUT)
		val datain = Bits(width = datawidth).flip
		val read = Bool(INPUT)
		val dataout = Bits(width = datawidth)
		val full = Bool(OUTPUT)
		val empty = Bool(OUTPUT)
	}
	val head = Reg(init = Bits(0,width = 12))
	val tail = Reg(init = Bits(0,width = 12))
	val addrwidth = log2Up(depth)
	//val buffer=Reg(init = Vec.fill(depth){Bits(0,width = datawidth)})
	val buffer=Mem(depth,Bits(width = datawidth))
	
	val nowfull = ((head(addrwidth-1,0) === tail(addrwidth-1,0)) && (head(addrwidth) =/= tail(addrwidth)))
	val willfull = io.write && (head(addrwidth-1,0) === (tail+Bits(1))(addrwidth-1,0)) && (head(addrwidth) =/= (tail+Bits(1))(addrwidth))
	val nowempty = ((head(addrwidth-1,0) === tail(addrwidth-1,0)) && (head(addrwidth) === tail(addrwidth)))
	val willempty = (io.read && ((head+Bits(1))(addrwidth-1,0) === tail(addrwidth-1,0)) && ((head+Bits(1))(addrwidth) === tail(addrwidth)))
	io.full := nowfull || willfull
	io.empty := nowempty || willempty
	
	//val outbuf=Reg(init = Bits(0,width = datawidth))
	
	when(io.rst){
		head := Bits(0)
		tail := Bits(0)
	}
	when(!io.rst && io.write){
		when(!nowfull){
			buffer(tail(addrwidth-1,0)) := io.datain
			tail := tail + Bits(1)
		}.otherwise{}
	}
	when(!io.rst && io.read){
		when(!nowempty){
			head := head + Bits(1)
		}.otherwise{}
	}.otherwise{}
	io.dataout := buffer(head(addrwidth-1,0))
}

class DFI_stoptbuf(size: Int, addrwidth: Int)(implicit p: Parameters) extends Module {
	val io = new Bundle {
		val rst = Bool(INPUT)
		val rw = Bits(width = 1)
		val id = Bits(width = 16)
		val taraddr = Bits(width = addrwidth)
		val red = Bool(OUTPUT)
	}
}

class DFILdOptBuf(size: Int, addrwidth: Int)(implicit p: Parameters) extends Module {
	val io = new Bundle{
		val rst = Bool(INPUT)
		val rw = Bits(width = 1).flip
		val id = Bits(width = 16).flip
		val taraddr = Bits(width = addrwidth).flip
		val red = Bool(OUTPUT)
	}
	val buf_id=Reg(init = Vec.fill(size){Bits(0,width = 16)})
	val buf_taraddr=Reg(init = Vec.fill(size){Bits(0,width = addrwidth)})
	
	val sameid=Vec.fill(size){Bool(false)}
	val sametaraddr=Vec.fill(size){Bool(false)}
	
	for (i<- 0 to size-1){
		sameid(i) := (io.id === buf_id(i))
		sametaraddr(i) := (io.taraddr === buf_taraddr(i))
	}
	
	buf_id(0) := Mux(io.rst,Bits(0),Mux(io.rw === Bits(1), io.id, buf_id(0)))
	buf_taraddr(0) := Mux(io.rst,Bits(0),Mux(io.rw === Bits(1), io.taraddr, buf_taraddr(0)))
	for (i<- 1 to size-1){
		buf_id(i) := Mux(io.rst,Bits(0),Mux(io.rw === Bits(1), buf_id(i-1), Mux(sametaraddr(i), Bits(0), buf_id(i))))
		buf_taraddr(i) := Mux(io.rst,Bits(0),Mux(io.rw === Bits(1), buf_taraddr(i-1), Mux(sametaraddr(i), Bits(0), buf_taraddr(i))))
	}
	
	val sameboth=Vec.fill(size){Bool(false)}
	for (i<- 0 to size-1){
		sameboth(i) := (sameid(i)&&sametaraddr(i))
	}
	
	val midre=Vec.fill(size){Vec.fill(size){Bool(false)}}
	for (i<- 0 to log2Up(size)-1){
		for (j<- 0 to (1<<i)-1){
			if(i<log2Up(size)-1){
				midre(i)(j) := midre(i+1)(j<<1) | midre(i+1)((j<<1)+1)
			}else{
				midre(i)(j) := sameboth(j<<1) | sameboth((j<<1)+1)
			}
		}
	}
	io.red := midre(0)(0)
}

class  DFIcheck(opcodes: OpcodeSet)(implicit p: Parameters) extends LazyRoCC(opcodes) {
  override lazy val module = new DFIcheckImp(this)
}

class DFIcheckImp2(outer: DFIcheck)(implicit p: Parameters) extends LazyRoCCModuleImp(outer)
    with HasCoreParameters {
}

class DFIcheckImp(outer: DFIcheck)(implicit p: Parameters) extends LazyRoCCModuleImp(outer)
    with HasCoreParameters {
	//IMPORTANT!!!!!: this module works under the assumption that the lowest 3 bits of rdsaddr, rdtaddr and debugaddr are all 0
	//info format:
	//21-20 = 0 : 19 = 0 : 16:st/ld, 15-0: id
	//21-20 = 0 : 19 = 1 : 18:ld?, 17:st?, 16:call/ret? 15-0: id
	//21-20 = 1 : rdt addr
	//21-20 = 2 : rds addr
	//21-20 = 3 : debug addr, 19 = 1 : run with debug
	//21-20 = 3 : debug addr, 18 = 1 : write debug
	//21-20 = 3 : debug addr, 17 = 1 : func signal
	
	def SIMULATION: Bool = Bool(false)
	
	def fifostldsize: Int = 64
	def fifofuncsize: Int = 8
	
	def ldopbufsize: Int = 64
	
	def shdstacksize: Int = 1024
	
	def bufsize: Int = 8
	
	def addrshift: Int = 2
	
	def rdscacheaddrwidth: Int = 10
	def rdsaddrwidth: Int = 16 //log(how many entry), not how many bytes
	def rdscacheaddrmask: UInt = UInt((1<<rdscacheaddrwidth)-1)
	
	def rdsmapcacheaddrwidth: Int = 10
	def rdsmapaddrwidth: Int = 16 //log(how many entry), not how many bytes
	def rdsmapcacheaddrmask: UInt = UInt((1<<rdsmapcacheaddrwidth)-1)
	
	//def rdtcacheaddrwidth: Int = 5
	//def rdtaddrwidth: Int = 8 //log(how many entry), not how many bytes
	def rdtcacheaddrwidth: Int = 10
	def rdtaddrwidth: Int = 25 //log(how many entry), not how many bytes
	def addrmask: Int = (1<<rdtaddrwidth)-1
	def rdtcacheaddrmask: UInt = UInt((1<<rdtcacheaddrwidth)-1)
	
	val addrrdt = Reg(init = Bits(0,width = xLen))
	val addrrds = Reg(init = Bits(0,width = xLen))
	val addrdebug = Reg(init = Bits(0,width = xLen))
	
	//target address acuqire
	val saddrbuf = Reg(init = Vec.fill(bufsize){Bits(0,width = xLen)})
	val scyclebuf = Reg(init = Vec.fill(bufsize){Bits(0,width = xLen)})
	val saddrbuf_p = Reg(init = Bits(0,width = xLen))
	val laddrbuf = Reg(init = Vec.fill(bufsize){Bits(0,width = xLen)})
	val lcyclebuf = Reg(init = Vec.fill(bufsize){Bits(0,width = xLen)})
	val laddrbuf_p = Reg(init = Bits(0,width = xLen))
	val staraddrs = Reg(init = Vec.fill(bufsize){Bits(0,width = xLen)})
	val staraddrs_p = Reg(init = Bits(0,width = xLen))
	val ltaraddrs = Reg(init = Vec.fill(bufsize){Bits(0,width = xLen)})
	val ltaraddrs_p = Reg(init = Bits(0,width = xLen))
	val staraddr = Reg(init = Bits(0,width = xLen))
	val ltaraddr = Reg(init = Bits(0,width = xLen))
	
	//DFI shared
	val count = Reg(init = Bits(0,width = 8))
	val countreq = Reg(init = Bits(0,width = 16))
	val countresp = Reg(init = Bits(0,width = 16))
	val countstinflight = Reg(init = Bits(0,width = 16))
	val matched = Reg(init = Bool(true))
	val info_debug = Reg(init = Bits(0,width = xLen))
	val infor = Reg(init = Bits(0,width = xLen))
	val haserror = Reg(init = Bool(false))
	val waitresp = Reg(init = Bool(false))
	
	//st/ld DFI
	val slneedprocess = Reg(init = Bits(0,width = 2))
	val taraddr = Reg(init = Vec.fill(2){Bits(0,width = xLen)})
	val rw = Reg(init = Bits(0,width = 1))
	val id = Reg(init = Vec.fill(3){Bits(0,width = xLen)})
	val prevwid = Reg(init = Bits(0,width = xLen))
	val rdsid = Reg(init = Bits(0,width = xLen))
	val rdsp = Reg(init = Bits(0,width = xLen))
	val rdss = Reg(init = Bits(0,width = xLen))
	val rdse = Reg(init = Bits(0,width = xLen))
	
	//func DFI
	val brneedprocess = Reg(init = Vec.fill(2){Bool(false)})
	val waitingcall = Reg(init = Bool(false))
	val repcall = Reg(init = Bool(false))
	val callremain = Reg(init = Vec.fill(3){Bool(false)})
	val brremain = Reg(init = Vec.fill(2){Bool(false)})
	val brtarget = Reg(init = Vec.fill(2){SInt(0,width = xLen)})
	val funcretaddr = Reg(init = Vec.fill(2){UInt(0,width = xLen)})
	val funcarg0 = Reg(init = Vec.fill(2){UInt(0,width = xLen)})
	val funcarg1 = Reg(init = Vec.fill(2){UInt(0,width = xLen)})
	val funcarg2 = Reg(init = Vec.fill(2){UInt(0,width = xLen)})
	val funcarg3 = Reg(init = Vec.fill(2){UInt(0,width = xLen)})
	val funcarg4 = Reg(init = Vec.fill(2){UInt(0,width = xLen)})
	val funcarg5 = Reg(init = Vec.fill(2){UInt(0,width = xLen)})
	val funcretpointer = Reg(init = Vec.fill(2){UInt(0,width = xLen)})
	val funcmode = Reg(init = Vec.fill(3){Bits(0,width = 2)})//000: normal call; 001: lib call, w; 010: lib call, r; 011: lib call, w+r; 100: ret 
	val funciscall = Reg(init = Vec.fill(2){Bool(false)})
	val funccount = Reg(init = Bits(0,width = xLen))
	val totalfunccount = Reg(init = Vec.fill(2){Bits(0,width = xLen)})
	
	//for stop
	val stop_call_count = Reg(init = Bits(0,width = xLen))
	val stop_call_count_all = Reg(init = Bits(0,width = xLen))
	val stop_call_count_max = Reg(init = Bits(0,width = xLen))
	val stop_call_count_keep = Reg(init = Bits(0,width = xLen))
	val stop_repcall = Reg(init = Bool(false))
	val stop_brtarget = Reg(init = SInt(0,width = xLen))
	val stop_coreexception = Reg(init = Bool(false))
	io.coreexception := stop_coreexception
	
	//for debug
	val opst_count = Reg(init = Bits(0,width = xLen))
	val opld_count = Reg(init = Bits(0,width = xLen))
	val uprdt_cycle = Reg(init = Bits(0,width = xLen))
	val rdrdt_cycle = Reg(init = Bits(0,width = xLen))
	val rdrdsmap_cycle = Reg(init = Bits(0,width = xLen))
	val rdrds_cycle = Reg(init = Bits(0,width = xLen))
	val chk_cycle = Reg(init = Bits(0,width = xLen))
	val maxlibarg0 = Reg(init = Bits(0,width = xLen))
	val maxlibarg1 = Reg(init = Bits(0,width = xLen))
	val maxlibarg2 = Reg(init = Bits(0,width = xLen))
	val maxlibarg3 = Reg(init = Bits(0,width = xLen))
	val maxlibarg4 = Reg(init = Bits(0,width = xLen))
	val maxlibarg5 = Reg(init = Bits(0,width = xLen))
	val maxlibretaddr = Reg(init = Bits(0,width = xLen))
	val maxlibretptr = Reg(init = Bits(0,width = xLen))
	val maxliblen = Reg(init = Bits(0,width = xLen))
	val totalldopt = Reg(init = Bits(0,width = xLen))
	val totalldnoopt = Reg(init = Bits(0,width = xLen))
	val totalrdtcachehit = Reg(init = Bits(0,width = xLen))
	val totalrdsmapcachehit = Reg(init = Bits(0,width = xLen))
	val totalrdscachehit = Reg(init = Bits(0,width = xLen))
	val totalrdtcachemiss = Reg(init = Bits(0,width = xLen))
	val totalrdsmapcachemiss = Reg(init = Bits(0,width = xLen))
	val totalrdscachemiss = Reg(init = Bits(0,width = xLen))
	val idle_cycle = Reg(init = Bits(0,width = xLen))
	val total_cycle = Reg(init = Bits(0,width = xLen))
	val fifocuspush_cycle = Reg(init = Bits(0,width = xLen))
	val fifofuncpush_cycle = Reg(init = Bits(0,width = xLen))
	val fifocuspop_cycle = Reg(init = Bits(0,width = xLen))
	val fifofuncpop_cycle = Reg(init = Bits(0,width = xLen))
	val fifocusbranchpush = Reg(init = Bits(0,width = xLen))
	val fifocusbranchpop = Reg(init = Bits(0,width = xLen))
	val fifofuncbranchpush = Reg(init = Bits(0,width = xLen))
	val fifofuncbranchpop = Reg(init = Bits(0,width = xLen))
	val fifofuncloss = Reg(init = Bits(0,width = xLen))
	val fifofunclatestinfo = Reg(init = Bits(0,width = xLen))
	val fifofunclatestlossinfo = Reg(init = Bits(0,width = xLen))
	val fifofunclatestlossrecord = Reg(init = Bits(0,width = xLen))
	val fifofunclatestlosscuscount = Reg(init = Bits(0,width = xLen))
	val fifofunclatestlossfunccount = Reg(init = Bits(0,width = xLen))
	val fifocusmax = Reg(init = Bits(0,width = xLen))
	val fifofuncmax = Reg(init = Bits(0,width = xLen))
	val fifofunclatestlosstaraddr = Reg(init = Bits(0,width = xLen))
	val fifofunclatesttaraddr = Reg(init = Bits(0,width = xLen))
	val shdstackmax = Reg(init = Bits(0,width = xLen))
	val totalstcount = Reg(init = Bits(0,width = xLen))
	val totalldcount = Reg(init = Bits(0,width = xLen))
	val totalcallcount = Reg(init = Bits(0,width = xLen))
	val totalcallexceptioncount = Reg(init = Bits(0,width = xLen))
	
	val rdtreaddebug_p = Reg(init = Bits(0,width = xLen))
	
	//latency check
	/*
	val lat_st_state = Reg(init = Bits(0,width = xLen))
	val lat_ld_state = Reg(init = Bits(0,width = xLen))
	val lat_ret_state = Reg(init = Bits(0,width = xLen))
	val lat_call_state = Reg(init = Bits(0,width = xLen))
	val lat_libst_state = Reg(init = Bits(0,width = xLen))
	val lat_libld_state = Reg(init = Bits(0,width = xLen))
	val lat_libstld_state = Reg(init = Bits(0,width = xLen))
	val lat_st_fifo_red = Reg(init = Bits(0,width = xLen))
	val lat_ld_fifo_red = Reg(init = Bits(0,width = xLen))
	val lat_call_fifo_red = Reg(init = Bits(0,width = xLen))
	val lat_libst_fifo_red = Reg(init = Bits(0,width = xLen))
	val lat_libld_fifo_red = Reg(init = Bits(0,width = xLen))
	val lat_libstld_fifo_red = Reg(init = Bits(0,width = xLen))
	val lat_ret_fifo_red = Reg(init = Bits(0,width = xLen))
	val lat_st_cycles = Reg(init = Bits(0,width = xLen))
	val lat_st_count = Reg(init = Bits(0,width = xLen))
	val lat_ld_cycles = Reg(init = Bits(0,width = xLen))
	val lat_ld_count = Reg(init = Bits(0,width = xLen))
	val lat_ret_cycles = Reg(init = Bits(0,width = xLen))
	val lat_ret_count = Reg(init = Bits(0,width = xLen))
	val lat_call_cycles = Reg(init = Bits(0,width = xLen))
	val lat_call_count = Reg(init = Bits(0,width = xLen))
	val lat_libst_cycles = Reg(init = Bits(0,width = xLen))
	val lat_libst_count = Reg(init = Bits(0,width = xLen))
	val lat_libld_cycles = Reg(init = Bits(0,width = xLen))
	val lat_libld_count = Reg(init = Bits(0,width = xLen))
	val lat_libstld_cycles = Reg(init = Bits(0,width = xLen))
	val lat_libstld_count = Reg(init = Bits(0,width = xLen))
	val lat_cate = Reg(init = Bits(0,width = xLen))
	*/
	//latency check FSM parameters
	def LAT_IDLE: Int = 0
	def LAT_WAIT_ENQ: Int = 1
	def LAT_WAIT_POP: Int = 2
	def LAT_WAIT_END: Int = 3
	
	//timeout
	val timeout = Reg(init = Bits(0,width = 16))
	def maxtimeout: Int = 300
	
	//record
	val violations=Reg(init = Bits(0,width = 16))
	val errorcount=Reg(init = Bits(0,width = 32))
	val totalcount=Reg(init = Bits(0,width = xLen))
	
	//opt buf
	val ldoptbuf = Module(new DFILdOptBuf(ldopbufsize, xLen)(outer.p))
	val ldopt_rst = Reg(init = Bool(false))
	val ldopt_rw = Reg(init = Bits(0,width = 1))
	val ldopt_id = Reg(init = Bits(0,width = 16))
	val ldopt_taraddr = Reg(init = Bits(0,width = xLen))
	ldoptbuf.io.rst := ldopt_rst
	ldoptbuf.io.rw := ldopt_rw
	ldoptbuf.io.id := ldopt_id
	ldoptbuf.io.taraddr := ldopt_taraddr
	val ldopt_red = Reg(init = Bool(false))

	//shadow stack
	//not use yet
	//val shdstack = Mem(shdstacksize, Bits(width = xLen))
	val shdstkaddr = Reg(init = Bits(0,width = xLen))
	val shdstkcount = Reg(init = Bits(0,width = xLen))
	val shdstkdatain = Reg(init = Bits(0,width = xLen))
	//val shdstkdataout = shdstack(shdstkaddr - Bits(1))
	//shdstack(shdstkaddr) := shdstkdatain
	
	//FIFO
	val fifocustom = Module(new Queue(Bits(width = 64+22), fifostldsize))
	val fifocusenqvalid = Reg(init = Bool(false))
	val fifocusdeqready = Reg(init = Bool(false))
	val fifocusdatain = Reg(init = Bits(0,width = 64+22))
	fifocustom.io.enq.valid := fifocusenqvalid
	fifocustom.io.enq.bits := fifocusdatain
	fifocustom.io.deq.ready := fifocusdeqready
	val fifocuscount = Reg(init = Bits(0,width = xLen))
	val waitinginfifocus = Reg(init = Bool(false))
	
	val fifofunc = Module(new Queue(Bits(width = 2+1+1+16+64+64*3), fifofuncsize))
	val fifofuncenqvalid = Reg(init = Bool(false))
	val fifofuncdeqready = Reg(init = Bool(false))
	val fifofuncdatain = Reg(init = Bits(0,width = 2+1+1+16+64+64*3))
	fifofunc.io.enq.valid := fifofuncenqvalid
	fifofunc.io.enq.bits := fifofuncdatain
	fifofunc.io.deq.ready := fifofuncdeqready
	val fifofunccount = Reg(init = Bits(0,width = xLen))
	val waitinginfifofunc = Reg(init = Bool(false))
	val waitingcallfifo = Reg(init = Bool(false))
	
	/*
	val testfifo = Module(new Queue(Bits(width = 8), 4))
	val fifotestvalid = Reg(init = Bool(false))
	val fifotestready = Reg(init = Bool(false))
	val fifotestdatain = Reg(init = Bits(0,width = 8))
	testfifo.io.enq.valid := fifotestvalid
	testfifo.io.enq.bits := fifotestdatain
	testfifo.io.deq.ready := fifotestready
	val fifostate = Reg(init = Bits(0,width = xLen))
	
	when(fifostate<Bits(4)){
		when(testfifo.io.enq.fire()){
			printf("enq data %x\n",fifotestdatain)
			fifostate := fifostate + Bits(1)
			fifotestvalid := Bool(false)
		}.otherwise{
			fifotestvalid := Bool(true)
			fifotestdatain := fifostate + Bits(1)
		}
	}.otherwise{
		when(testfifo.io.deq.fire()){
			printf("deq data %x\n",testfifo.io.deq.bits)
		}.otherwise{
			fifotestready := Bool(true)
		}
	}
	*/
	/*
	val fifocustom=Mem(4,Bits(width = 32))
	val fifocusaddrwidth=log2Up(4)
	val fifocushead = Reg(init = Bits(0,width = xLen))
	val fifocustail = Reg(init = Bits(0,width = xLen))
	val fifocusdatain = Reg(init = Bits(0,width = xLen))
	fifocustom(fifocustail(fifocusaddrwidth-1,0)) := fifocusdatain
	val fifocusdataout=fifocustom(fifocushead(fifocusaddrwidth-1,0))
	*/
	/*
	val fifocustom = Module(new DFIFIFO(4, 32)(outer.p))
	val fifocusrst = Reg(init = Bool(false))
	val fifocuswrite = Reg(init = Bool(false))
	val fifocusdatain = Reg(init = Bits(0,width = xLen))
	val fifocusread = Reg(init = Bool(false))
	fifocustom.io.rst := fifocusrst
	fifocustom.io.write := fifocuswrite
	fifocustom.io.datain := fifocusdatain
	fifocustom.io.read := fifocusread
	
	val fifostate = Reg(init = Bits(0,width = xLen))
	
	fifostate := fifostate + Bits(1)
	
	when(fifostate <= Bits(20)){
	when(!fifocustom.io.full){
		fifocuswrite := Bool(true)
		fifocusdatain := io.dfi.bits.cycle_now+Bits(1)
		printf("FIFO write %x\n",io.dfi.bits.cycle_now+Bits(1))
	}.otherwise{
		fifocuswrite := Bool(false)
	}
	
	when(!fifocustom.io.empty){
		fifocusread := Bool(true)
		fifocusdatain := io.dfi.bits.cycle_now
		printf("not empty FIFO read %x\n",fifocustom.io.dataout)
	}.otherwise{
		printf("FIFO read %x\n",fifocustom.io.dataout)
		fifocusread := Bool(false)
	}
	}
	*/
	/*
	when(fifostate===Bits(0)){
		when(!fifocustom.io.full){
			fifocuswrite := Bool(true)
			fifocusdatain := io.dfi.bits.cycle_now+Bits(1)
			printf("FIFO write %x\n",io.dfi.bits.cycle_now+Bits(1))
		}.otherwise{
			fifocuswrite := Bool(false)
			fifostate := Bits(1)
		}
	}.elsewhen(fifostate<=Bits(15)){
		when(!fifocustom.io.empty){
			fifocusread := Bool(true)
			fifocusdatain := io.dfi.bits.cycle_now
			printf("not empty FIFO read %x\n",fifocustom.io.dataout)
		}.otherwise{
			printf("FIFO read %x\n",fifocustom.io.dataout)
			fifocusread := Bool(false)
			fifostate := fifostate + Bits(1)
		}
	}
	*/
	/*
	//funcmode 2, iscall 1, retpointer 64, arg0-2 64*3,
	val fifofunc = Module(new DFIFIFO(4, 2+1+64+64*3)(outer.p))
	val fifofuncrst = Reg(init = Bool(false))
	val fifofuncwrite = Reg(init = Bool(false))
	val fifofuncdatain = Reg(init = Bits(0,width = 2+1+64+64*3))
	val fifofuncread = Reg(init = Bool(false))
	fifofunc.io.rst := fifofuncrst
	fifofunc.io.write := fifofuncwrite
	fifofunc.io.datain := fifofuncdatain
	fifofunc.io.read := fifofuncread
	*/
	
	//FSM parameters
	def IDLE: Int = 0
	def WAIT: Int = 1
	def WRITERDT: Int = 2
	def READRDT: Int = 3
	def READRDSMAP: Int = 4
	def READRDS: Int = 5
	def CHECK: Int = 6
	def FUNCSTATE: Int = 7
	def WRITELDTRACE: Int = 8
	def WRITEDEBUG: Int = 9
	def ERROR: Int = 10
	def REPORT: Int = 11
	def READRDTRDSMAP: Int = 12
	def READRDTCACHE: Int = 13
	def READRDSMAPCACHE: Int = 14
	def CACHERESET: Int = 15
	def READSTOPCOND: Int = 16
	
	val state = Reg(init = Bits(IDLE,width = 8))
	val nstate = Reg(init = Bits(IDLE,width = 8))
	val pstate = Reg(init = Bits(IDLE,width = 8))
	
	val ldstate = Reg(init = Bits(IDLE,width = 8))
	
	//control
	val busystld = Reg(init = Bool(false))
	val busyfunc = Reg(init = Bool(false))
	val stallfunc = Reg(init = Bool(false))
	val stallstld = Reg(init = Bool(false))
	
    io.corestall := stallfunc || stallstld
	//val cmd = Queue(io.cmd)
	val cmd = io.cmd
	cmd.ready := !(busystld || busyfunc) //&& (violations === Bits(0))
	
	//wb, not use
	io.resp.valid := Bool(false)//cmd.valid && cmd.bits.inst.xd && io.mem.req.ready
	io.resp.bits.rd := cmd.bits.inst.rd
	io.resp.bits.data := Bits(0)//wdata
	io.busy := Bool(false) //cmd.valid || busy
	io.interrupt := Bool(false)
	
	//get the DFI info
	val info=(cmd.bits.inst.funct<<15)|(cmd.bits.inst.rs2<<10)|(cmd.bits.inst.rs1<<5)|(cmd.bits.inst.rd)
	
	//memory control
	val memvalid = Reg(init = Bool(false))
	val memaddr=Reg(init = Bits(0,width = xLen))
	val memaddrmod=Reg(init = Bits(0,width = xLen))
	val memtag = Reg(init = Bits(0,width = xLen))//used for record the memtype of the request
	val memdatain=Reg(init = Bits(0,width = xLen))
	val memdataout=Mux(memtag(1) === Bits(1),Mux(memtag(0) === Bits(1), io.mem.resp.bits.data, io.mem.resp.bits.data(31,0)), Mux(memtag(0) === Bits(1), io.mem.resp.bits.data(15,0), io.mem.resp.bits.data(7,0)))
	val memw=Reg(init = Bool(false))
	val memtype=Reg(init = Bits(0,width = xLen))
	
	val pmemaddr=Reg(init = Bits(0,width = xLen))
	val pmemdatain=Reg(init = Bits(0,width = xLen))
	val pmemw=Reg(init = Bool(false))
	val pmemtype=Reg(init = Bits(0,width = xLen))
	
	//val memaddrfix = Mux((memaddr>=Bits(0xf4084000L) && memaddr<Bits(0xf4084000L+0x6000000L)), memaddr, Bits(0xf4084000L+0x4400000L))
	val memaddrfix = memaddr
	
	val memoffdata = memdatain<<(((memaddrfix(2,0))<<3))
	
	io.mem.req.valid := memvalid//cmd.valid && ((funct === UInt(1)) || (funct === UInt(2))) // && !busy
	io.mem.req.bits.addr := memaddrfix
	io.mem.req.bits.tag := memtag
	io.mem.req.bits.cmd := Mux(memw,M_XWR,M_XRD)//M_XRD // perform a load (M_XWR for stores)
	io.mem.req.bits.typ := Mux(memtype(1) === Bits(1),Mux(memtype(0) === Bits(1), MT_D, MT_W), Mux(memtype(0) === Bits(1), MT_H, MT_B))//memtype // D = 8 bytes, W = 4, H = 2, B = 1
	io.mem.req.bits.data := memoffdata
	io.mem.req.bits.phys := Mux(SIMULATION,Bool(false),Bool(true))//Bool(true)
	
	//caches
	val rdtcache = Mem((1<<rdtcacheaddrwidth), Bits(width = rdtaddrwidth-rdtcacheaddrwidth+64))
	val rdtcwaddr = Reg(init = Bits(0,width = rdtcacheaddrwidth))
	val rdtcraddr = Reg(init = Bits(0,width = rdtcacheaddrwidth))
	val rdtcdatain = Reg(init = Bits(0,width = rdtaddrwidth-rdtcacheaddrwidth-2+64))
	val rdtcdataout = rdtcache(rdtcraddr)
	rdtcache(rdtcwaddr) := rdtcdatain
	val rdtcprobing = Reg(init = Bool(false))
	
	val rdsmapcache = Mem((1<<rdsmapcacheaddrwidth), Bits(width = rdsmapaddrwidth-rdsmapcacheaddrwidth+64))
	val rdsmapcwaddr = Reg(init = Bits(0,width = rdsmapcacheaddrwidth))
	val rdsmapcraddr = Reg(init = Bits(0,width = rdsmapcacheaddrwidth))
	val rdsmapcdatain = Reg(init = Bits(0,width = rdsmapaddrwidth-rdsmapcacheaddrwidth+64))
	val rdsmapcdataout = rdsmapcache(rdsmapcraddr)
	rdsmapcache(rdsmapcwaddr) := rdsmapcdatain
	val rdsmapcprobing = Reg(init = Bool(false))
	
	val rdscache = Mem((1<<rdscacheaddrwidth), Bits(width = rdsaddrwidth-rdscacheaddrwidth+64))
	val rdscwaddr = Reg(init = Bits(0,width = rdscacheaddrwidth))
	val rdscraddr = Reg(init = Bits(0,width = rdscacheaddrwidth))
	val rdscdatain = Reg(init = Bits(0,width = rdsaddrwidth-rdscacheaddrwidth+64))
	val rdscdataout = rdscache(rdscraddr)
	rdscache(rdscwaddr) := rdscdatain
	val rdscprobing = Reg(init = Bool(false))
	
	//dfi check
	val checkmatch0=(rdsp)<rdse+memaddrmod(2,1) && (rdsp)>=rdss+memaddrmod(2,1) && io.mem.resp.bits.data(15,0) === prevwid
	val checkmatch1=(rdsp+Bits(1))<rdse+memaddrmod(2,1) && (rdsp+Bits(1))>=rdss+memaddrmod(2,1) && io.mem.resp.bits.data(31,16) === prevwid
	val checkmatch2=(rdsp+Bits(2))<rdse+memaddrmod(2,1) && (rdsp+Bits(2))>=rdss+memaddrmod(2,1) && io.mem.resp.bits.data(47,32) === prevwid
	val checkmatch3=(rdsp+Bits(3))<rdse+memaddrmod(2,1) && (rdsp+Bits(3))>=rdss+memaddrmod(2,1) && io.mem.resp.bits.data(63,48) === prevwid
	val onematched=(checkmatch0 || checkmatch1) || (checkmatch2 || checkmatch3)
	
	val ccheckmatch0=(rdsp)<rdse+memaddrmod(2,1) && (rdsp)>=rdss+memaddrmod(2,1) && rdscdataout(15,0) === prevwid
	val ccheckmatch1=(rdsp+Bits(1))<rdse+memaddrmod(2,1) && (rdsp+Bits(1))>=rdss+memaddrmod(2,1) && rdscdataout(31,16) === prevwid
	val ccheckmatch2=(rdsp+Bits(2))<rdse+memaddrmod(2,1) && (rdsp+Bits(2))>=rdss+memaddrmod(2,1) && rdscdataout(47,32) === prevwid
	val ccheckmatch3=(rdsp+Bits(3))<rdse+memaddrmod(2,1) && (rdsp+Bits(3))>=rdss+memaddrmod(2,1) && rdscdataout(63,48) === prevwid
	val conematched=(ccheckmatch0 || ccheckmatch1) || (ccheckmatch2 || ccheckmatch3)
	
	when(io.dfi.bits.valid){
		when(io.dfi.bits.cmd===M_XWR){
			saddrbuf(saddrbuf_p(log2Up(bufsize)-1,0)) := io.dfi.bits.taraddr
			scyclebuf(saddrbuf_p(log2Up(bufsize)-1,0)) := io.dfi.bits.cycle
			saddrbuf_p := saddrbuf_p + Bits(1)
		}
		.elsewhen(io.dfi.bits.cmd===M_XRD){
			laddrbuf(laddrbuf_p(log2Up(bufsize)-1,0)) := io.dfi.bits.taraddr
			lcyclebuf(laddrbuf_p(log2Up(bufsize)-1,0)) := io.dfi.bits.cycle
			laddrbuf_p := laddrbuf_p + Bits(1)
		}.otherwise{}
	}.otherwise{}

	//this is for debug
	when(cmd.fire()){
		staraddrs(staraddrs_p(log2Up(bufsize)-1,0)) := saddrbuf(saddrbuf_p-Bits(1))
		staraddrs_p := staraddrs_p + Bits(1)
		
		ltaraddrs(ltaraddrs_p(log2Up(bufsize)-1,0)) := laddrbuf(laddrbuf_p-Bits(1))
		ltaraddrs_p := ltaraddrs_p + Bits(1)
	}.otherwise{}
	
	val taraddr_recorded=Reg(init = Bool(false))
	when(cmd.valid && cmd.ready){
		when(!taraddr_recorded){
			staraddr := saddrbuf(saddrbuf_p-Bits(1))
			ltaraddr := laddrbuf(laddrbuf_p-Bits(1))
		}
		taraddr_recorded := Bool(false)
	}.elsewhen(cmd.valid && !cmd.ready){
		when(!taraddr_recorded){
			taraddr_recorded := Bool(true)
			staraddr := saddrbuf(saddrbuf_p-Bits(1))
			ltaraddr := laddrbuf(laddrbuf_p-Bits(1))
		}
	}.otherwise{}
	
	//recieve func request
	//note that func req and st/ld req may come at the same cycle, accually it is func req is earlier than st/ld req, because the delay
	//funcmode 2, iscall 1, callremain 1, id 16, retpointer 64, arg0-2 64*3,
	when(fifofuncmax < fifofunccount){
		fifofuncmax := fifofunccount
	}.otherwise{}
	
	when(fifofunc.io.enq.fire() && fifofunc.io.deq.fire()){
	}.elsewhen(fifofunc.io.enq.fire()){
		fifofunccount := fifofunccount + Bits(1)
	}.elsewhen(fifofunc.io.deq.fire()){
		when(fifofunccount > Bits(0)){
			fifofunccount := fifofunccount - Bits(1)
		}
	}.otherwise{}

	when(fifofunc.io.enq.fire()){
		fifofuncpush_cycle := fifofuncpush_cycle + Bits(1)
		fifofuncenqvalid := Bool(false)
		waitinginfifofunc := Bool(false)
		busyfunc := Bool(false)
	}.elsewhen(waitinginfifofunc){
		fifofuncpush_cycle := fifofuncpush_cycle + Bits(1)
		busyfunc := Bool(true)
	}.otherwise{
		busyfunc := Bool(false)
	}
	//receive func FIFO
	when(fifofunc.io.deq.fire()){
		fifofuncbranchpop := fifofuncbranchpop + Bits(1)
		fifofuncdeqready := Bool(false)
		waitingcallfifo := Bool(false)
		when(fifofunc.io.deq.bits(64*4+17) === Bits(1)){//call
			when((fifofunc.io.deq.bits(64*3-1,64*2) & UInt((1<<addrshift)-1)) > UInt(0)){
				totalfunccount(0) := (fifofunc.io.deq.bits(64*3-1,64*2)>>addrshift)+UInt(1)
			}.otherwise{
				totalfunccount(0) := (fifofunc.io.deq.bits(64*3-1,64*2)>>addrshift)
			}
			
			id(1):=fifofunc.io.deq.bits(64*4+16-1,64*4)
			funcmode(1) := fifofunc.io.deq.bits(64*4+19,64*4+18)
			callremain(1) := fifofunc.io.deq.bits(64*4+16)
			funciscall(0) := Bool(true)
			funcarg0(0) := fifofunc.io.deq.bits(64*1-1,0)
			funcarg1(0) := fifofunc.io.deq.bits(64*2-1,64*1)
			funcarg2(0) := fifofunc.io.deq.bits(64*3-1,64*2)
			//funcretaddr(0) := io.dfi.bits.funcretaddr
			funcretpointer(0) := fifofunc.io.deq.bits(64*4-1,64*3) //return address's pointer
			brremain(0) := Bool(true)
			
			printf(">>>>> 2.5 func FIFO OUT, @%x\n",io.dfi.bits.cycle_now)
			printf("function call, from FIFO, %x\n",fifofunc.io.deq.bits)
			printf("<<<<< 2.5\n")
			brneedprocess(0) := Bool(true)
		}.otherwise{//ret
			
			funcmode(1) := fifofunc.io.deq.bits(64*4+19,64*4+18)
			callremain(1) := fifofunc.io.deq.bits(64*4+16)
			funciscall(0) := Bool(false)
			funcarg0(0) := UInt(0)
			funcarg1(0) := UInt(0)
			funcarg2(0) := UInt(0)
			funcretpointer(0) := fifofunc.io.deq.bits(64*4-1,64*3) //return address's pointer
			brremain(0) := Bool(true)
			
			printf(">>>>> 2.5 func FIFO OUT, @%x\n",io.dfi.bits.cycle_now)
			printf("function ret, from FIFO, %x\n",fifofunc.io.deq.bits)
			printf("<<<<< 2.5\n")
			brneedprocess(0) := Bool(true)
		}
	}
	.otherwise{
		when(state === Bits(IDLE) && slneedprocess === Bits(0) && !brneedprocess(0) && waitingcallfifo){
			fifofuncdeqready := Bool(true)
		}.otherwise{
			fifofuncdeqready := Bool(false)
		}
		/*
		when(fifofuncdeqready && timeout<UInt(maxtimeout)){
			timeout := timeout + Bits(1)
		}.elsewhen(fifofuncdeqready && timeout>=UInt(maxtimeout)){
			fifofuncdeqready := Bool(false)
			waitingcallfifo := Bool(false)
			fifofuncloss := fifofuncloss + Bits(1)
			fifofunclatestlossinfo := fifofunclatestinfo
			fifofunclatestlosstaraddr := fifofunclatesttaraddr
			fifofunclatestlosscuscount := fifocuscount
			fifofunclatestlossfunccount := fifofunccount
			fifofunclatestlossrecord := (fifofuncdeqready<<28)|(fifofuncenqvalid<<27)|(fifocusdeqready<<26)|(fifocusenqvalid<<25)|(waitinginfifofunc<<24)|(waitinginfifocus<<23)|(stallfunc<<22)|(busystld<<21)|(brneedprocess(0)<<20)|(slneedprocess<<18)|(waitingcallfifo<<17)|(waitingcall<<16)|state
			timeout := Bits(0)
		}.otherwise{
			timeout := Bits(0)
		}*/
	}
	
	//receive st/ld request
	when(fifocusmax < fifocuscount){
		fifocusmax := fifocuscount
	}.otherwise{}
	
	when(fifocustom.io.enq.fire() && fifocustom.io.deq.fire()){
	}.elsewhen(fifocustom.io.enq.fire()){
		fifocuscount := fifocuscount + Bits(1)
	}.elsewhen(fifocustom.io.deq.fire()){
		when(fifocuscount > Bits(0)){
			fifocuscount := fifocuscount - Bits(1)
		}
	}.otherwise{}
	
	when(fifocustom.io.enq.fire()){
		fifocuspush_cycle := fifocuspush_cycle + Bits(1)
		fifocusenqvalid := Bool(false)
		waitinginfifocus := Bool(false)
		busystld := Bool(false)
	}.elsewhen(waitinginfifocus){
		fifocuspush_cycle := fifocuspush_cycle + Bits(1)
		busystld := Bool(true)
	}.otherwise{
		busystld := Bool(false)
	}
	when(cmd.fire()){
		fifocuspush_cycle := fifocuspush_cycle + Bits(1)
		when(info(21,20) === UInt(1)){
			//addrrdt := Bits(0x200074e010L)
			when(!SIMULATION){
				//addrrdt := Bits(0xe0400000L)
				addrrdt := Bits(0xf4084000L+0x400000L)
			}.otherwise{
				when(taraddr_recorded){
					addrrdt := staraddr//saddrbuf(saddrbuf_p-Bits(1))
					printf("rdt: %x, (now cycle: %x)\n", staraddr,io.dfi.bits.cycle_now)//saddrbuf(saddrbuf_p-Bits(1)))
				}.otherwise{
					addrrdt := saddrbuf(saddrbuf_p-Bits(1))
					printf("rdt: %x, (now cycle: %x)\n", saddrbuf(saddrbuf_p-Bits(1)),io.dfi.bits.cycle_now)
				}
			}
		}
		.elsewhen(info(21,20) === UInt(2)){
			//addrrds := Bits(0x200474f010L)
			when(!SIMULATION){
				//addrrds := Bits(0xe4400000L)
				addrrds := Bits(0xf4084000L+0x4400000L)
			}.otherwise{
				when(taraddr_recorded){
					addrrds := staraddr//saddrbuf(saddrbuf_p-Bits(1))
					printf("rds: %x\n", staraddr)//saddrbuf(saddrbuf_p-Bits(1)))
				}.otherwise{
					addrrds := saddrbuf(saddrbuf_p-Bits(1))
					printf("rds: %x\n", saddrbuf(saddrbuf_p-Bits(1)))
				}
			}
		}.elsewhen(info(21,20) === UInt(3)){
			//addrdebug := Bits(0x200034d010L)
			when(info(17) === Bits(1)){
				
			}.elsewhen(info(18) === Bits(1)){
				fifocusdatain := info
				waitinginfifocus := Bool(true)
				fifocusenqvalid := Bool(true)
				busystld := Bool(true)
			}.otherwise{
				when(!SIMULATION){
					//addrdebug := Bits(0xe0000000L)
					addrdebug := Bits(0xf4084000L)
				}.otherwise{
					when(taraddr_recorded){
						addrdebug := staraddr//saddrbuf(saddrbuf_p-Bits(1))
						printf("debug buffer: %x\n", staraddr)//saddrbuf(saddrbuf_p-Bits(1)))
					}.otherwise{
						addrdebug := saddrbuf(saddrbuf_p-Bits(1))
						printf("debug buffer: %x\n", saddrbuf(saddrbuf_p-Bits(1)))
					}
				}
				fifocusdatain := info
				waitinginfifocus := Bool(true)
				fifocusenqvalid := Bool(true)
				busystld := Bool(true)
			}
		}.otherwise{
			when(info(21,20) === UInt(0) && addrrdt =/= Bits(0) && addrrds =/= Bits(0) && addrdebug =/= Bits(0)){
				printf(">>>>> 1 ld/st FIFO IN, @%x\n",io.dfi.bits.cycle_now)
				printf("DFI request recieved\n")
				printf("info: %x\n",info)
				printf("thread pointer %x\n",io.dfi.bits.threadptr)
				printf("%x/%x DFI process errors\n",errorcount,totalcount)
				printf("return address: %x, stack address: %x, func arg0: %x, func arg1: %x, func arg2: %x\n",io.dfi.bits.funcretaddr,io.dfi.bits.funcstackaddr,io.dfi.bits.funcarg0,io.dfi.bits.funcarg1,io.dfi.bits.funcarg2)
				printf("<<<<< 1 \n")
				when(info(19,17) > UInt(0)){
					fifocusbranchpush := fifocusbranchpush + Bits(1)
					fifocusdatain := (((fifofuncdeqready<<28)|(fifofuncenqvalid<<27)|(fifocusdeqready<<26)|(fifocusenqvalid<<25)|(waitinginfifofunc<<24)|(waitinginfifocus<<23)|(stallfunc<<22)|(busystld<<21)|(brneedprocess(0)<<20)|(slneedprocess<<18)|(waitingcallfifo<<17)|(waitingcall<<16)|state)<<(22+8))|(fifocuscount<<22)|info
					when(info(16) === Bits(0)){
						fifofuncbranchpush := fifofuncbranchpush + Bits(1)
						fifofuncdatain := (info(18,17)<<(64*4+18))|(Bits(1)<<(64*4+17))|(Mux(info(18,17) > Bits(0),Bits(1),Bits(0))<<(64*4+16))|(info(15,0)<<(64*4))|((io.dfi.bits.funcstackaddr - UInt(8))<<(64*3))|(io.dfi.bits.funcarg2<<(64*2))|(io.dfi.bits.funcarg1<<UInt(64))|(io.dfi.bits.funcarg0)
						printf(">>>>> 1.5 func FIFO IN, @%x\n",io.dfi.bits.cycle_now)
						printf("function call, to %x\n",io.dfi.bits.brtarget)
						printf("return address: %x, stack address: %x, func arg0: %x, func arg1: %x, func arg2: %x\n",io.dfi.bits.funcretaddr,io.dfi.bits.funcstackaddr,io.dfi.bits.funcarg0,io.dfi.bits.funcarg1,io.dfi.bits.funcarg2)
						printf("push to FIFO: %x\n",(info(18,17)<<(64*4+18))|(Bits(1)<<(64*4+17))|(Mux(info(18,17) > Bits(0),Bits(1),Bits(0))<<(64*4+16))|(info(15,0)<<(64*4))|((io.dfi.bits.funcstackaddr - UInt(8))<<(64*3))|(io.dfi.bits.funcarg2<<(64*2))|(io.dfi.bits.funcarg1<<UInt(64))|(io.dfi.bits.funcarg0))
						printf("<<<<< 1.5\n")
					}.otherwise{
						fifofuncbranchpush := fifofuncbranchpush + Bits(1)
						fifofuncdatain := (info(18,17)<<(64*4+18))|(Bits(0)<<(64*4+17))|(Mux(info(18,17) > Bits(0),Bits(1),Bits(0))<<(64*4+16))|(info(15,0)<<(64*4))|((io.dfi.bits.funcstackaddr - UInt(8))<<(64*3))
						printf(">>>>> 1.5 func FIFO IN, @%x\n",io.dfi.bits.cycle_now)
						printf("function return, to %x\n",io.dfi.bits.brtarget)
						printf("return address: %x, stack address: %x, func arg0: %x, func arg1: %x, func arg2: %x\n",io.dfi.bits.funcretaddr,io.dfi.bits.funcstackaddr,io.dfi.bits.funcarg0,io.dfi.bits.funcarg1,io.dfi.bits.funcarg2)
						printf("push to FIFO: %x\n",(info(18,17)<<(64*4+18))|(Bits(0)<<(64*4+17))|(Mux(info(18,17) > Bits(0),Bits(1),Bits(0))<<(64*4+16))|(info(15,0)<<(64*4))|((io.dfi.bits.funcstackaddr - UInt(8))<<(64*3)))
						printf("<<<<< 1.5\n")
					}
					waitinginfifofunc := Bool(true)
					fifofuncenqvalid := Bool(true)
					busyfunc := Bool(true)
					
					when(info(16) === Bits(0) && info(18,17) > Bits(0) && maxliblen<(io.dfi.bits.funcarg2>>addrshift)){
						maxlibarg0 := io.dfi.bits.funcarg0
						maxlibarg1 := io.dfi.bits.funcarg1
						maxlibarg2 := io.dfi.bits.funcarg2
						maxlibarg3 := io.dfi.bits.funcarg3
						maxlibarg4 := io.dfi.bits.funcarg4
						maxlibarg5 := info
						maxlibretaddr := io.dfi.bits.funcretaddr
						maxlibretptr := io.dfi.bits.funcstackaddr
						maxliblen := io.dfi.bits.funcarg2>>addrshift
					}.otherwise{}
					
				}.elsewhen(info(16) === UInt(0)){
					when(taraddr_recorded){
						fifocusdatain := (staraddr<<22)|info
					}.otherwise{
						fifocusdatain := (saddrbuf(saddrbuf_p-Bits(1))<<22)|info
					}
				}.otherwise{//ld
					when(taraddr_recorded){
						fifocusdatain := (ltaraddr<<22)|info
					}.otherwise{
						fifocusdatain := (laddrbuf(laddrbuf_p-Bits(1))<<22)|info
					}
				}
				waitinginfifocus := Bool(true)
				fifocusenqvalid := Bool(true)
				busystld := Bool(true)
			}
		}
	}
	//receive st/ld FIFO
	when(fifocustom.io.deq.fire()){
		fifocusdeqready := Bool(false)
		when(fifocustom.io.deq.bits(21,20) === UInt(1)){
		}.elsewhen(fifocustom.io.deq.bits(21,20) === UInt(2)){
		}.elsewhen(fifocustom.io.deq.bits(21,20) === UInt(3)){
			printf(">>>>> 2 FUNC FIFO OUT, @%x\n",io.dfi.bits.cycle_now)
			printf("info: %x\n",fifocustom.io.deq.bits)
			printf("<<<<< 2\n")
			when(fifocustom.io.deq.bits(18)){
				infor := fifocustom.io.deq.bits
				slneedprocess := Bits(2)
			}.otherwise{
				infor := fifocustom.io.deq.bits
				rdsmapcwaddr := Bits(0)//ready to reset rdsmapcache
				rdsmapcdatain := Bits(0)
				rdtcwaddr := Bits(0)
				rdtcdatain := Bits(0)
				rdscwaddr := Bits(0)
				rdscdatain := Bits(0)
				fifocusbranchpush := Bits(0)
				fifocusbranchpop := Bits(0)
				fifofuncbranchpush := Bits(0)
				fifofuncbranchpop := Bits(0)
				fifofuncloss := Bits(0)
				fifocusmax := Bits(0)
				fifofuncmax := Bits(0)
				stop_call_count := Bits(0)
				
				slneedprocess := Bits(2)
				
				when(fifocustom.io.deq.bits(19) === UInt(1)){//debug
					printf("WRTIE LOAD TRACE\n")
					ldstate := Bits(WRITELDTRACE)
				}.otherwise{
					ldstate := Bits(READRDSMAP)
				}
				rdtreaddebug_p := Bits(0)
			}
		}.elsewhen(fifocustom.io.deq.bits(21,20) === UInt(0) && addrrdt =/= Bits(0) && addrrds =/= Bits(0) && addrdebug =/= Bits(0)){
			printf(">>>>> 2 FUNC FIFO OUT, @%x\n",io.dfi.bits.cycle_now)
			printf("info: %x\n",fifocustom.io.deq.bits)
			printf("<<<<< 2\n")
			infor := fifocustom.io.deq.bits
			when(fifocustom.io.deq.bits(19,17) > UInt(0)){
				fifocusbranchpop := fifocusbranchpop + Bits(1)
				waitingcallfifo := Bool(true)
				fifofunclatestinfo := fifocustom.io.deq.bits(21,0)
				fifofunclatesttaraddr := fifocustom.io.deq.bits(22+64-1,22)
			}.elsewhen(fifocustom.io.deq.bits(16) === UInt(0)){
				taraddr(0) := fifocustom.io.deq.bits(64+22-1,22)
				slneedprocess := Bits(2)
			}.otherwise{//ld
				taraddr(0) := fifocustom.io.deq.bits(64+22-1,22)
				slneedprocess := Bits(2)
			}
		}
		.otherwise{}
	}
	.otherwise{
		when(state === Bits(IDLE) && slneedprocess === Bits(0) && !brneedprocess(0) && !waitingcallfifo){
			fifocusdeqready := Bool(true)
		}.otherwise{
			fifocusdeqready := Bool(false)
		}
	}
	/*
	//lat check
	//1st lat check----------call
	when(lat_call_state===Bits(LAT_IDLE)){
		when(cmd.fire()){
			when(info(21,20) === UInt(1)){
				//addrrdt := Bits(0x200074e010L)
			}.elsewhen(info(21,20) === UInt(2)){
				//addrrds := Bits(0x200474f010L)
			}.elsewhen(info(21,20) === UInt(3)){
				//addrdebug := Bits(0x200034d010L)
			}.otherwise{
				when(info(21,20) === UInt(0) && addrrdt =/= Bits(0) && addrrds =/= Bits(0) && addrdebug =/= Bits(0)){
					when(info(19,17) > UInt(0)){
						when(info(16) === Bits(0)){//call
							when(info(17)===UInt(0) && info(18)===UInt(0)){//normal call
								lat_cate := Bits(0)
								lat_call_count := lat_call_count + Bits(1)
								lat_call_state := Bits(LAT_WAIT_ENQ)
							}
							when(!fifocustom.io.deq.fire()){
								lat_call_fifo_red := fifocuscount + Bits(1)
							}.otherwise{
								lat_call_fifo_red := fifocuscount
							}
						}.otherwise{//ret
						}
					}.elsewhen(info(16) === UInt(0)){
						//store
					}.otherwise{
						//ld
					}
				}
			}
		}.otherwise{}
	}.elsewhen(lat_call_state===Bits(LAT_WAIT_ENQ)){
		when(!io.corestalled){
			lat_call_cycles := lat_call_cycles + Bits(1)
		}
		when(lat_call_fifo_red>Bits(0) && fifocustom.io.deq.fire()){
			lat_call_fifo_red := lat_call_fifo_red - Bits(1)
		}
		when(fifocustom.io.enq.fire()){
			lat_call_state := Bits(LAT_WAIT_POP)
		}
	}.elsewhen(lat_call_state===Bits(LAT_WAIT_POP)){
		when(!io.corestalled){
			lat_call_cycles := lat_call_cycles + Bits(1)
		}
		when(lat_call_fifo_red>Bits(0) && fifocustom.io.deq.fire()){
			lat_call_fifo_red := lat_call_fifo_red - Bits(1)
		}
		when(lat_call_fifo_red===Bits(0)){
			when(state===Bits(IDLE) && (slneedprocess === Bits(2) || brneedprocess(0))){
				lat_call_state := Bits(LAT_WAIT_END)
			}
		}.otherwise{}
	}.elsewhen(lat_call_state===Bits(LAT_WAIT_END)){
		when(!io.corestalled){
			lat_call_cycles := lat_call_cycles + Bits(1)
		}
		when(state===Bits(IDLE)){
			lat_call_state := Bits(LAT_IDLE)
		}
	}.otherwise{
		lat_call_state := Bits(LAT_IDLE)
	}
	//2nd lat check----------libst
	when(lat_libst_state===Bits(LAT_IDLE)){
		when(cmd.fire()){
			when(info(21,20) === UInt(1)){
				//addrrdt := Bits(0x200074e010L)
			}.elsewhen(info(21,20) === UInt(2)){
				//addrrds := Bits(0x200474f010L)
			}.elsewhen(info(21,20) === UInt(3)){
				//addrdebug := Bits(0x200034d010L)
			}.otherwise{
				when(info(21,20) === UInt(0) && addrrdt =/= Bits(0) && addrrds =/= Bits(0) && addrdebug =/= Bits(0)){
					when(info(19,17) > UInt(0)){
						when(info(16) === Bits(0)){//call
							when(info(17)===UInt(1) && info(18)===UInt(0)){//write
								lat_cate := Bits(1)
								lat_libst_count := lat_libst_count + Bits(1)
								lat_libst_state := Bits(LAT_WAIT_ENQ)
							}
							when(!fifocustom.io.deq.fire()){
								lat_libst_fifo_red := fifocuscount + Bits(1)
							}.otherwise{
								lat_libst_fifo_red := fifocuscount
							}
						}.otherwise{//ret
						}
					}.elsewhen(info(16) === UInt(0)){
						//store
					}.otherwise{
						//ld
					}
				}
			}
		}.otherwise{}
	}.elsewhen(lat_libst_state===Bits(LAT_WAIT_ENQ)){
		when(!io.corestalled){
			lat_libst_cycles := lat_libst_cycles + Bits(1)
		}
		when(lat_libst_fifo_red>Bits(0) && fifocustom.io.deq.fire()){
			lat_libst_fifo_red := lat_libst_fifo_red - Bits(1)
		}
		when(fifocustom.io.enq.fire()){
			lat_libst_state := Bits(LAT_WAIT_POP)
		}
	}.elsewhen(lat_libst_state===Bits(LAT_WAIT_POP)){
		when(!io.corestalled){
			lat_libst_cycles := lat_libst_cycles + Bits(1)
		}
		when(lat_libst_fifo_red>Bits(0) && fifocustom.io.deq.fire()){
			lat_libst_fifo_red := lat_libst_fifo_red - Bits(1)
		}
		when(lat_libst_fifo_red===Bits(0)){
			when(state===Bits(IDLE) && (slneedprocess === Bits(2) || brneedprocess(0))){
				lat_libst_state := Bits(LAT_WAIT_END)
			}
		}.otherwise{}
	}.elsewhen(lat_libst_state===Bits(LAT_WAIT_END)){
		when(!io.corestalled){
			lat_libst_cycles := lat_libst_cycles + Bits(1)
		}
		when(state===Bits(IDLE)){
			lat_libst_state := Bits(LAT_IDLE)
		}
	}.otherwise{
		lat_libst_state := Bits(LAT_IDLE)
	}
	//3rd lat check----------libld
	when(lat_libld_state===Bits(LAT_IDLE)){
		when(cmd.fire()){
			when(info(21,20) === UInt(1)){
				//addrrdt := Bits(0x200074e010L)
			}.elsewhen(info(21,20) === UInt(2)){
				//addrrds := Bits(0x200474f010L)
			}.elsewhen(info(21,20) === UInt(3)){
				//addrdebug := Bits(0x200034d010L)
			}.otherwise{
				when(info(21,20) === UInt(0) && addrrdt =/= Bits(0) && addrrds =/= Bits(0) && addrdebug =/= Bits(0)){
					when(info(19,17) > UInt(0)){
						when(info(16) === Bits(0)){//call
							when(info(17)===UInt(0) && info(18)===UInt(0)){//normal call
							}.elsewhen(info(17)===UInt(1) && info(18)===UInt(0)){//write
							}.elsewhen(info(17)===UInt(0) && info(18)===UInt(1)){//read
								lat_cate := Bits(2)
								lat_libld_count := lat_libld_count + Bits(1)
								lat_libld_state := Bits(LAT_WAIT_ENQ)
							}.otherwise{
							}
							when(!fifocustom.io.deq.fire()){
								lat_libld_fifo_red := fifocuscount + Bits(1)
							}.otherwise{
								lat_libld_fifo_red := fifocuscount
							}
						}.otherwise{//ret
						}
					}.elsewhen(info(16) === UInt(0)){
						//store
					}.otherwise{
						//ld
					}
				}
			}
		}.otherwise{}
	}.elsewhen(lat_libld_state===Bits(LAT_WAIT_ENQ)){
		when(!io.corestalled){
			lat_libld_cycles := lat_libld_cycles + Bits(1)
		}
		when(lat_libld_fifo_red>Bits(0) && fifocustom.io.deq.fire()){
			lat_libld_fifo_red := lat_libld_fifo_red - Bits(1)
		}
		when(fifocustom.io.enq.fire()){
			lat_libld_state := Bits(LAT_WAIT_POP)
		}
	}.elsewhen(lat_libld_state===Bits(LAT_WAIT_POP)){
		when(!io.corestalled){
			lat_libld_cycles := lat_libld_cycles + Bits(1)
		}
		when(lat_libld_fifo_red>Bits(0) && fifocustom.io.deq.fire()){
			lat_libld_fifo_red := lat_libld_fifo_red - Bits(1)
		}
		when(lat_libld_fifo_red===Bits(0)){
			when(state===Bits(IDLE) && (slneedprocess === Bits(2) || brneedprocess(0))){
				lat_libld_state := Bits(LAT_WAIT_END)
			}
		}.otherwise{}
	}.elsewhen(lat_libld_state===Bits(LAT_WAIT_END)){
		when(!io.corestalled){
			lat_libld_cycles := lat_libld_cycles + Bits(1)
		}
		when(state===Bits(IDLE)){
			lat_libld_state := Bits(LAT_IDLE)
		}
	}.otherwise{
		lat_libld_state := Bits(LAT_IDLE)
	}
	//4th lat check----------libstld
	when(lat_libstld_state===Bits(LAT_IDLE)){
		when(cmd.fire()){
			when(info(21,20) === UInt(1)){
				//addrrdt := Bits(0x200074e010L)
			}.elsewhen(info(21,20) === UInt(2)){
				//addrrds := Bits(0x200474f010L)
			}.elsewhen(info(21,20) === UInt(3)){
				//addrdebug := Bits(0x200034d010L)
			}.otherwise{
				when(info(21,20) === UInt(0) && addrrdt =/= Bits(0) && addrrds =/= Bits(0) && addrdebug =/= Bits(0)){
					when(info(19,17) > UInt(0)){
						when(info(16) === Bits(0)){//call
							when(info(17)===UInt(0) && info(18)===UInt(0)){//normal call
							}.elsewhen(info(17)===UInt(1) && info(18)===UInt(0)){//write
							}.elsewhen(info(17)===UInt(0) && info(18)===UInt(1)){//read
							}.otherwise{
								lat_cate := Bits(3)
								lat_libstld_count := lat_libstld_count + Bits(1)
								lat_libstld_state := Bits(LAT_WAIT_ENQ)
							}
							when(!fifocustom.io.deq.fire()){
								lat_libstld_fifo_red := fifocuscount + Bits(1)
							}.otherwise{
								lat_libstld_fifo_red := fifocuscount
							}
						}.otherwise{//ret
						}
					}.elsewhen(info(16) === UInt(0)){
						//store
					}.otherwise{
						//ld
					}
				}
			}
		}.otherwise{}
	}.elsewhen(lat_libstld_state===Bits(LAT_WAIT_ENQ)){
		when(!io.corestalled){
			lat_libstld_cycles := lat_libstld_cycles + Bits(1)
		}
		when(lat_libstld_fifo_red>Bits(0) && fifocustom.io.deq.fire()){
			lat_libstld_fifo_red := lat_libstld_fifo_red - Bits(1)
		}
		when(fifocustom.io.enq.fire()){
			lat_libstld_state := Bits(LAT_WAIT_POP)
		}
	}.elsewhen(lat_libstld_state===Bits(LAT_WAIT_POP)){
		when(!io.corestalled){
			lat_libstld_cycles := lat_libstld_cycles + Bits(1)
		}
		when(lat_libstld_fifo_red>Bits(0) && fifocustom.io.deq.fire()){
			lat_libstld_fifo_red := lat_libstld_fifo_red - Bits(1)
		}
		when(lat_libstld_fifo_red===Bits(0)){
			when(state===Bits(IDLE) && (slneedprocess === Bits(2) || brneedprocess(0))){
				lat_libstld_state := Bits(LAT_WAIT_END)
			}
		}.otherwise{}
	}.elsewhen(lat_libstld_state===Bits(LAT_WAIT_END)){
		when(!io.corestalled){
			lat_libstld_cycles := lat_libstld_cycles + Bits(1)
		}
		when(state===Bits(IDLE)){
			lat_libstld_state := Bits(LAT_IDLE)
		}
	}.otherwise{
		lat_libstld_state := Bits(LAT_IDLE)
	}
	//5th lat check----------ret
	when(lat_ret_state===Bits(LAT_IDLE)){
		when(cmd.fire()){
			when(info(21,20) === UInt(1)){
				//addrrdt := Bits(0x200074e010L)
			}.elsewhen(info(21,20) === UInt(2)){
				//addrrds := Bits(0x200474f010L)
			}.elsewhen(info(21,20) === UInt(3)){
				//addrdebug := Bits(0x200034d010L)
			}.otherwise{
				when(info(21,20) === UInt(0) && addrrdt =/= Bits(0) && addrrds =/= Bits(0) && addrdebug =/= Bits(0)){
					when(info(19,17) > UInt(0)){
						when(info(16) === Bits(0)){//call
						}.otherwise{//ret
							when(!fifocustom.io.deq.fire()){
								lat_ret_fifo_red := fifocuscount + Bits(1)
							}.otherwise{
								lat_ret_fifo_red := fifocuscount
							}
							lat_cate := Bits(4)
							lat_ret_count := lat_ret_count + Bits(1)
							lat_ret_state := Bits(LAT_WAIT_ENQ)
						}
					}.elsewhen(info(16) === UInt(0)){
						//store
					}.otherwise{
						//ld
					}
				}
			}
		}.otherwise{}
	}.elsewhen(lat_ret_state===Bits(LAT_WAIT_ENQ)){
		when(!io.corestalled){
			lat_ret_cycles := lat_ret_cycles + Bits(1)
		}
		when(lat_ret_fifo_red>Bits(0) && fifocustom.io.deq.fire()){
			lat_ret_fifo_red := lat_ret_fifo_red - Bits(1)
		}
		when(fifocustom.io.enq.fire()){
			lat_ret_state := Bits(LAT_WAIT_POP)
		}
	}.elsewhen(lat_ret_state===Bits(LAT_WAIT_POP)){
		when(!io.corestalled){
			lat_ret_cycles := lat_ret_cycles + Bits(1)
		}
		when(lat_ret_fifo_red>Bits(0) && fifocustom.io.deq.fire()){
			lat_ret_fifo_red := lat_ret_fifo_red - Bits(1)
		}
		when(lat_ret_fifo_red===Bits(0)){
			when(state===Bits(IDLE) && (slneedprocess === Bits(2) || brneedprocess(0))){
				lat_ret_state := Bits(LAT_WAIT_END)
			}
		}.otherwise{}
	}.elsewhen(lat_ret_state===Bits(LAT_WAIT_END)){
		when(!io.corestalled){
			lat_ret_cycles := lat_ret_cycles + Bits(1)
		}
		when(state===Bits(IDLE)){
			lat_ret_state := Bits(LAT_IDLE)
		}
	}.otherwise{
		lat_ret_state := Bits(LAT_IDLE)
	}
	//6th lat check----------st
	when(lat_st_state===Bits(LAT_IDLE)){
		when(cmd.fire()){
			when(info(21,20) === UInt(1)){
				//addrrdt := Bits(0x200074e010L)
			}.elsewhen(info(21,20) === UInt(2)){
				//addrrds := Bits(0x200474f010L)
			}.elsewhen(info(21,20) === UInt(3)){
				//addrdebug := Bits(0x200034d010L)
			}.otherwise{
				when(info(21,20) === UInt(0) && addrrdt =/= Bits(0) && addrrds =/= Bits(0) && addrdebug =/= Bits(0)){
					when(info(19,17) > UInt(0)){
					}.elsewhen(info(16) === UInt(0)){
						//store
						when(!fifocustom.io.deq.fire()){
							lat_st_fifo_red := fifocuscount + Bits(1)
						}.otherwise{
							lat_st_fifo_red := fifocuscount
						}
						lat_cate := Bits(5)
						lat_st_count := lat_st_count + Bits(1)
						lat_st_state := Bits(LAT_WAIT_ENQ)
					}.otherwise{
						//ld
					}
				}
			}
		}.otherwise{}
	}.elsewhen(lat_st_state===Bits(LAT_WAIT_ENQ)){
		when(!io.corestalled){
			lat_st_cycles := lat_st_cycles + Bits(1)
		}
		when(lat_st_fifo_red>Bits(0) && fifocustom.io.deq.fire()){
			lat_st_fifo_red := lat_st_fifo_red - Bits(1)
		}
		when(fifocustom.io.enq.fire()){
			lat_st_state := Bits(LAT_WAIT_POP)
		}
	}.elsewhen(lat_st_state===Bits(LAT_WAIT_POP)){
		when(!io.corestalled){
			lat_st_cycles := lat_st_cycles + Bits(1)
		}
		when(lat_st_fifo_red>Bits(0) && fifocustom.io.deq.fire()){
			lat_st_fifo_red := lat_st_fifo_red - Bits(1)
		}
		when(lat_st_fifo_red===Bits(0)){
			when(state===Bits(IDLE) && (slneedprocess === Bits(2) || brneedprocess(0))){
				lat_st_state := Bits(LAT_WAIT_END)
			}
		}.otherwise{}
	}.elsewhen(lat_st_state===Bits(LAT_WAIT_END)){
		when(!io.corestalled){
			lat_st_cycles := lat_st_cycles + Bits(1)
		}
		when(state===Bits(IDLE)){
			lat_st_state := Bits(LAT_IDLE)
		}
	}.otherwise{
		lat_st_state := Bits(LAT_IDLE)
	}
	//7th lat check----------ld
	when(lat_ld_state===Bits(LAT_IDLE)){
		when(cmd.fire()){
			when(info(21,20) === UInt(1)){
				//addrrdt := Bits(0x200074e010L)
			}.elsewhen(info(21,20) === UInt(2)){
				//addrrds := Bits(0x200474f010L)
			}.elsewhen(info(21,20) === UInt(3)){
				//addrdebug := Bits(0x200034d010L)
			}.otherwise{
				when(info(21,20) === UInt(0) && addrrdt =/= Bits(0) && addrrds =/= Bits(0) && addrdebug =/= Bits(0)){
					when(info(19,17) > UInt(0)){
					}.elsewhen(info(16) === UInt(0)){
						//store
					}.otherwise{
						//ld
						when(!fifocustom.io.deq.fire()){
							lat_ld_fifo_red := fifocuscount + Bits(1)
						}.otherwise{
							lat_ld_fifo_red := fifocuscount
						}
						lat_cate := Bits(6)
						lat_ld_count := lat_ld_count + Bits(1)
						lat_ld_state := Bits(LAT_WAIT_ENQ)
					}
				}
			}
		}.otherwise{}
	}.elsewhen(lat_ld_state===Bits(LAT_WAIT_ENQ)){
		when(!io.corestalled){
			lat_ld_cycles := lat_ld_cycles + Bits(1)
		}
		when(lat_ld_fifo_red>Bits(0) && fifocustom.io.deq.fire()){
			lat_ld_fifo_red := lat_ld_fifo_red - Bits(1)
		}
		when(fifocustom.io.enq.fire()){
			lat_ld_state := Bits(LAT_WAIT_POP)
		}
	}.elsewhen(lat_ld_state===Bits(LAT_WAIT_POP)){
		when(!io.corestalled){
			lat_ld_cycles := lat_ld_cycles + Bits(1)
		}
		when(lat_ld_fifo_red>Bits(0) && fifocustom.io.deq.fire()){
			lat_ld_fifo_red := lat_ld_fifo_red - Bits(1)
		}
		when(lat_ld_fifo_red===Bits(0)){
			when(state===Bits(IDLE) && (slneedprocess === Bits(2) || brneedprocess(0))){
				lat_ld_state := Bits(LAT_WAIT_END)
			}
		}.otherwise{}
	}.elsewhen(lat_ld_state===Bits(LAT_WAIT_END)){
		when(!io.corestalled){
			lat_ld_cycles := lat_ld_cycles + Bits(1)
		}
		when(state===Bits(IDLE)){
			lat_ld_state := Bits(LAT_IDLE)
		}
	}.otherwise{
		lat_ld_state := Bits(LAT_IDLE)
	}
	*/
	//ldopt
	when(fifofunc.io.deq.fire()){
		ldopt_rst := Bool(true)
		ldopt_red := Bool(false)
	}.elsewhen(fifocustom.io.deq.fire() && fifocustom.io.deq.bits(21,20) =/= UInt(0)){
		ldopt_rst := Bool(true)
		ldopt_red := Bool(false)
	}.elsewhen(fifocustom.io.deq.fire() && fifocustom.io.deq.bits(21,20) === UInt(0) && addrrdt =/= Bits(0) && addrrds =/= Bits(0) && addrdebug =/= Bits(0)){
		ldopt_rst := Bool(false)
		ldopt_rw := fifocustom.io.deq.bits(16)
		ldopt_id := fifocustom.io.deq.bits(15,0)
		when(fifocustom.io.deq.bits(16)===Bits(0)){
			ldopt_taraddr := fifocustom.io.deq.bits(64+22-1,22)
		}.otherwise{
			ldopt_taraddr := fifocustom.io.deq.bits(64+22-1,22)
		}
		ldopt_red := Bool(false)
	}.otherwise{
		when(ldoptbuf.io.red && ldopt_id =/= Bits(0)){
			ldopt_red := Bool(true)
			printf("!!!!!!!! redundant ld\n")
		}
		ldopt_rst := Bool(false)
		ldopt_rw := Bits(0)
		ldopt_id := Bits(0)
		ldopt_taraddr := Bits(0)
	}
	
	when(cmd.fire() && info(21,20) === UInt(3) && info(18) =/= Bits(1)){
		total_cycle := Bits(0)
	}.otherwise{
		total_cycle := total_cycle + Bits(1)
	}
	
	when(cmd.fire() && info(21,20) === UInt(3) && info(18) =/= Bits(1)){
		idle_cycle := Bits(0)
	}.elsewhen(state === Bits(IDLE)){
		idle_cycle := idle_cycle + Bits(1)
	}.otherwise{}
	
	when(shdstkaddr>shdstackmax){
		shdstackmax := shdstkaddr
	}.otherwise{}
	
	//stop the program
	when(cmd.fire() && info(21,20) === UInt(3) && info(17) === UInt(1)){
		printf("CALL COUNT SIGNAL\n")
		when(stop_call_count_max > Bits(0)){
			printf("current stop count: %x\n",stop_call_count)
			stop_call_count := stop_call_count + Bits(1)
			stop_call_count_all := stop_call_count_all + Bits(1)
		}
	}
	/*
	when(io.dfi.bits.callvalid && (!stop_repcall || stop_brtarget =/= io.dfi.bits.brtarget)){
		stop_repcall := Bool(true)
		stop_brtarget := io.dfi.bits.brtarget
		when(stop_call_count_max > Bits(0)){
			stop_call_count := stop_call_count + Bits(1)
			stop_call_count_all := stop_call_count_all + Bits(1)
		}
	}.elsewhen(io.dfi.bits.retvalid && (!stop_repcall || stop_brtarget =/= io.dfi.bits.brtarget)){
		stop_repcall := Bool(false)
		stop_brtarget := io.dfi.bits.brtarget
	}.elsewhen(!io.dfi.bits.callvalid && !io.dfi.bits.retvalid){
		stop_repcall := Bool(true)
	}.otherwise{}
	*/
	when(stop_call_count_max > Bits(0) && stop_call_count >= stop_call_count_max){
		stop_call_count_max := Bits(0)
		stop_call_count := Bits(0)
		printf("EXCEPTION!!!\n")
		stop_coreexception := Bool(true)
	}
	.elsewhen(stop_coreexception){
		printf("WAIT FOR NEXT CUSTOM TO STOP\n")
		when(io.canexception){
			stop_coreexception := Bool(false)
		}.otherwise{}
	}
	/*.elsewhen(stop_coreexception && stop_call_count_keep < Bits(5)){
		stop_call_count_keep := stop_call_count_keep + Bits(1)
	}*/
	.otherwise{
		stop_call_count_keep := Bits(0)
		stop_coreexception := Bool(false)
	}
	
	//++++++++++++++++++++++FSM++++++++++++++++++
	when(state === Bits(IDLE)){
		stallfunc := Bool(false)
		memtag := Bits(0)
		memtype := Bits(3)
		count := Bits(0)
		memvalid := Bool(false)
		memw := Bool(false)
		nstate := Bits(IDLE)
		pstate := Bits(IDLE)
		matched := Bool(true)
		waitresp := Bool(false)
		when(~matched){
			violations := violations + Bits(1)
			printf("IDLESTATE: violation\n");
		}.otherwise{}
		when(haserror){
			errorcount := errorcount + Bits(1)
			haserror := Bool(false)
		}
		
		when(brneedprocess(0)){//need to process a function call
			brneedprocess(0) := Bool(false)
			brneedprocess(1) := brneedprocess(0)
			
			totalcount := totalcount + Bits(1)
			totalcallcount := totalcallcount + Bits(1)
			printf("---------------DFI process begin --------FUNC, @%x\n",io.dfi.bits.cycle_now)
			printf("normal function call/ret? %x, is call? %x, is lib? %x, mode: %x\n",brremain(0),funciscall(0),callremain(1),funcmode(1))
			printf("return address pointer: %x arg0: %x, arg1: %x, arg2: %x\n",funcretpointer(0),funcarg0(0),funcarg1(0),funcarg2(0))
			printf("custom inst fire? %x\n",cmd.fire())
			printf("%x/%x DFI process errors\n",errorcount,totalcount)
			
			id(2):=id(1)
			funccount := totalfunccount(0)
			totalfunccount(1) := totalfunccount(0)
			funcmode(2) := funcmode(1)
			callremain(2) := callremain(1)
			funciscall(1) := funciscall(0)
			brtarget(1) := brtarget(0)
			funcarg0(1) := funcarg0(0)
			funcarg1(1) := funcarg1(0)
			funcarg2(1) := funcarg2(0)
			funcretaddr(1) := funcretaddr(0)
			funcretpointer(1) := funcretpointer(0)
			brremain(1) := brremain(0)
			
			when(brremain(0)){
				when(funciscall(0)){
					when(funcmode(1) >= Bits(2)){
						//stallfunc := Bool(true)
					}
					/*
					shdstkaddr := shdstkaddr + Bits(1)
					when(shdstkcount < shdstacksize){
						shdstkaddr := shdstkcount
						shdstkcount := shdstkcount + Bits(1)
						sdhstkdatain := 
					}.otherwise{
						
					}*/
					val rdtentry = (((funcretpointer(0)>>addrshift)&Bits(addrmask))<<1)(61,3)
					rdtcraddr := rdtentry&rdtcacheaddrmask
					
					memaddr := addrrdt+(((funcretpointer(0)>>addrshift)&Bits(addrmask))<<1)
					memtype := Bits(1)
					memtag := Bits(2)
					memw := Bool(true)
					memvalid := Bool(true)
					memdatain := Bits(0xffff)
				
					state := Bits(WRITERDT)
				}.otherwise{
					val rdtentry = (((funcretpointer(0)>>addrshift)&Bits(addrmask))<<1)(61,3)
					rdtcraddr := rdtentry&rdtcacheaddrmask
					rdtcprobing := Bool(true)
					
					val realaddr = addrrdt+(((funcretpointer(0)>>addrshift)&Bits(addrmask))<<1)
					memaddr := realaddr(61,3)<<3
					memaddrmod := realaddr(2,0)
					memtype := Bits(3)
					memtag := Bits(2)
					memw := Bool(false)
					memvalid := Bool(false)
					
					state := Bits(READRDT)//skip READRDSMAP because no need
				}
			}.otherwise{}
		}.otherwise{}
		
		when(!brneedprocess(0) && slneedprocess === Bits(2)){
			printf("---------------DFI process begin, @%x\n",io.dfi.bits.cycle_now)
			printf("info: %x\n",infor)
			brneedprocess(1) := Bool(false)
			totalcount := totalcount + Bits(1)
			/*
			printf("sbuf_p %d +++\n", saddrbuf_p(log2Up(bufsize)-1,0))
			for (i<- 0 to bufsize-1)
			printf("sbuf %d value: %x, cycle %x\n", UInt(i), saddrbuf(UInt(i)), scyclebuf(UInt(i)))
			
			printf("lbuf_p %d +++\n", laddrbuf_p(log2Up(bufsize)-1,0))
			for (i<- 0 to bufsize-1)
			printf("lbuf %d value: %x, cycle %x\n", UInt(i), laddrbuf(UInt(i)), lcyclebuf(UInt(i)))
			*/
			slneedprocess := Bits(0)
			count := Bits(0)
			rw := infor(16)
			id(2) := infor(15,0)
			info_debug := infor
			when(infor(21,20) === UInt(3)){
				when(infor(18)){
					printf("write report ---\n")
					count := Bits(0)
					state := Bits(REPORT)
					brremain(1) := Bool(false)
					callremain(2) := Bool(false)
				}.otherwise{
					printf("reset rdsmap cache ---\n")
					brremain(1) := Bool(false)
					callremain(2) := Bool(false)
					state := Bits(CACHERESET)
				}
			}.elsewhen(infor(19,17) > UInt(0)){
				
			}.elsewhen(infor(16) === UInt(0)){//store
				totalstcount := totalstcount + Bits(1)
				val rdtentry = (((taraddr(0)>>addrshift)&Bits(addrmask))<<1)(61,3)
				rdtcraddr := rdtentry&rdtcacheaddrmask
				
				memaddr := addrrdt+(((taraddr(0)>>addrshift)&Bits(addrmask))<<1)
				memtype := Bits(1)
				memtag := Bits(2)
				memw := Bool(true)
				memvalid := Bool(true)
				memdatain := infor(15,0)
				
				brremain(1) := Bool(false)
				callremain(2) := Bool(false)
				taraddr(1) := taraddr(0)
				state := Bits(WRITERDT)
			}.otherwise{//load
				totalldcount := totalldcount + Bits(1)
				when((ldopt_red || (ldoptbuf.io.red && ldopt_id =/= Bits(0)))){
					totalldopt := totalldopt + Bits(1)
					printf("LD redundant\n")
					brremain(1) := Bool(false)
					callremain(2) := Bool(false)
				}.otherwise{
					totalldnoopt := totalldnoopt + Bits(1)
					when(ldstate === Bits(READRDSMAP)){
						val rdsmapentry = infor(15,0)
						rdsmapcraddr := rdsmapentry&rdsmapcacheaddrmask
						rdsmapcprobing := Bool(true)
						
						memaddr := addrrds+(infor(15,0)<<3)
						memw := Bool(false)
						memtype := Bits(3)
						memtag := Bits(2)
						memvalid := Bool(false)
						//memvalid := Bool(true)
						
						brremain(1) := Bool(false)
						callremain(2) := Bool(false)
						taraddr(1) := taraddr(0)
						state := Bits(READRDSMAP)
					}.otherwise{
						brremain(1) := Bool(false)
						callremain(2) := Bool(false)
						taraddr(1) := taraddr(0)
						state := Bits(WRITELDTRACE)
					}
				}
			}
		}.otherwise{}
	}
	.elsewhen(state === Bits(CACHERESET)){
		when(rdsmapcwaddr<Bits((1<<rdsmapcacheaddrwidth)-1)){
			rdsmapcwaddr := rdsmapcwaddr + Bits(1)
			rdsmapcdatain := Bits(0)
		}.otherwise{}
		when(rdtcwaddr<Bits((1<<rdtcacheaddrwidth)-1)){
			rdtcwaddr := rdtcwaddr + Bits(1)
			rdtcdatain := Bits(0)
		}.otherwise{}
		when(rdscwaddr<Bits((1<<rdscacheaddrwidth)-1)){
			rdscwaddr := rdscwaddr + Bits(1)
			rdscdatain := Bits(0)
		}.otherwise{}
		when(rdsmapcwaddr === Bits((1<<rdsmapcacheaddrwidth)-1) && rdtcwaddr === Bits((1<<rdtcacheaddrwidth)-1) && rdscwaddr === Bits((1<<rdscacheaddrwidth)-1)){
			printf("cache reset done\n")
			//state := Bits(IDLE)
			memaddr := addrdebug
			memw := Bool(false)
			memtype := Bits(3)
			memtag := Bits(2)
			when(io.mem.req.fire()){
				memvalid := Bool(false)
				state := Bits(READSTOPCOND)
			}.otherwise{
				memvalid := Bool(true)
			}
		}.otherwise{}
	}
	.elsewhen(state === Bits(READSTOPCOND)){
		when(io.mem.resp.valid && io.mem.resp.bits.tag(1,0) === Bits(2)){
			stop_call_count_max := io.mem.resp.bits.data
			printf("stop condition (call) is: %x\n",io.mem.resp.bits.data)
			state := Bits(IDLE)
		}
	}
	.elsewhen(state === Bits(WAIT)){
		when(io.mem.resp.valid && io.mem.resp.bits.tag(1,0) === Bits(2)){
			state := nstate
		}
	}
	.elsewhen(state === Bits(WRITERDT)){
		uprdt_cycle := uprdt_cycle + Bits(1)
		when(!waitresp){
			when(io.mem.req.fire()){
				val cachehit = rdtcdataout =/= Bits(0) && (rdtcdataout>>64) === ((memaddr-addrrdt)>>3)>>rdtcacheaddrwidth
				when(cachehit){
					rdtcwaddr := rdtcraddr
					rdtcdatain := (rdtcdataout&(~(Bits(0xffff)<<(memaddr(2,1)<<4))))|((memdatain&Bits(0xffff))<<(memaddr(2,1)<<4))
				}
				
				waitresp := Bool(true)
				printf("w addr: %x, data: %x, id: %x, RDT entry: %x\n",memaddr,memdatain,id(2),(memaddr-addrrdt)>>1)
				memvalid := Bool(false)
			}.otherwise{
				memvalid := Bool(true)
			}
		}.otherwise{
			when(io.mem.resp.valid && io.mem.resp.bits.tag(1,0) === Bits(2)){
				opst_count := opst_count + Bits(1)
				waitresp := Bool(false)
				when(brremain(1) && funciscall(1)){
					brremain(1) := Bool(false)
					when(callremain(2)){
						when(funccount === Bits(0) || funccount >= Bits(0xf0000000L)){
							totalcallexceptioncount := totalcallexceptioncount + Bits(1)
							printf("WARNING, lib func length incorrect\n")
							state := Bits(IDLE)
							memvalid := Bool(false)
						}.elsewhen(funcmode(2) === Bits(2) || funcmode(2) === Bits(3)){
							val rdsmapentry = id(2)
							rdsmapcraddr := rdsmapentry&rdsmapcacheaddrmask
							rdsmapcprobing := Bool(true)
							
							memaddr := addrrds+(id(2)<<3)
							memw := Bool(false)
							memtype := Bits(3)
							memtag := Bits(2)
							memvalid := Bool(false)
							//memvalid := Bool(true)
							
							state := Bits(READRDSMAP)
						}.elsewhen(funcmode(2) === Bits(1)){
							val rdtentry = ((((funcarg0(1)>>addrshift)+funccount-Bits(1))&Bits(addrmask))<<1)(61,3)
							rdtcraddr := rdtentry&rdtcacheaddrmask
							
							memaddr := addrrdt+((((funcarg0(1)>>addrshift)+funccount-Bits(1))&Bits(addrmask))<<1)
							memw := Bool(true)
							memtype := Bits(1)
							memtag := Bits(2)
							memvalid := Bool(true)
							memdatain := id(2)
							
							state := Bits(WRITERDT)
						}.otherwise{}
					}.otherwise{
						state := Bits(IDLE)
						memvalid := Bool(false)
					}
				}.elsewhen(!callremain(2)){
					state := Bits(IDLE)
					memvalid := Bool(false)
				}.elsewhen(callremain(2) && funccount <= Bits(1)){
					state := Bits(IDLE)
					memvalid := Bool(false)
				}.otherwise{
					val rdtentry = ((((funcarg0(1)>>addrshift)+funccount-Bits(2))&Bits(addrmask))<<1)(61,3)
					rdtcraddr := rdtentry&rdtcacheaddrmask
					
					memaddr := addrrdt+((((funcarg0(1)>>addrshift)+funccount-Bits(2))&Bits(addrmask))<<1)
					memw := Bool(true)
					memtype := Bits(1)
					memtag := Bits(2)
					memvalid := Bool(true)
					memdatain := id(2)
					
					state := Bits(WRITERDT)
					memvalid := Bool(true)
					funccount := funccount - Bits(1)
				}
			}.otherwise{
				memvalid := Bool(false)
			}
		}
	}
	.elsewhen(state === Bits(READRDSMAP)){
		rdrdsmap_cycle := rdrdsmap_cycle + Bits(1)
		rdsid := Bits(0)
		matched := Bool(false)
		when(waitresp || rdsmapcprobing){
			val cachehit = rdsmapcdataout =/= Bits(0) && (rdsmapcdataout>>64) === ((memaddr-addrrds)>>3)>>rdsmapcacheaddrwidth
			val memresp = io.mem.resp.valid && io.mem.resp.bits.tag(1,0) === Bits(2)
			when(cachehit || memresp){
				when(waitresp){
					val rdsmapentry = ((memaddr-addrrds)>>3)
					rdsmapcwaddr := rdsmapentry&rdsmapcacheaddrmask
					rdsmapcdatain := ((rdsmapentry>>rdsmapcacheaddrwidth)<<64)|io.mem.resp.bits.data(63,0)
				}.otherwise{}
				
				waitresp := Bool(false)
				rdsmapcprobing := Bool(false)
				
				when(waitresp){
					totalrdsmapcachemiss := totalrdsmapcachemiss + Bits(1)
					printf("rds range: %x, %x\n",io.mem.resp.bits.data(31,0),io.mem.resp.bits.data(63,32))
					rdss := io.mem.resp.bits.data(31,0)
					rdsp := io.mem.resp.bits.data(31,0)
					rdse := io.mem.resp.bits.data(63,32)
				}.otherwise{
					totalrdsmapcachehit := totalrdsmapcachehit + Bits(1)
					printf("RDSMAPCACHEHIT, rdsmapcache data: %x (%x, %x)\n",rdsmapcdataout,(rdsmapcdataout>>64),((memaddr-addrrds)>>3)>>rdsmapcacheaddrwidth)
					printf("rds range: %x, %x\n",rdsmapcdataout(31,0),rdsmapcdataout(63,32))
					rdss := rdsmapcdataout(31,0)
					rdsp := rdsmapcdataout(31,0)
					rdse := rdsmapcdataout(63,32)
				}
				
				when(waitresp && (io.mem.resp.bits.data(31,0) === io.mem.resp.bits.data(63,32)) || rdsmapcprobing && (rdsmapcdataout(31,0) === rdsmapcdataout(63,32))){//not need to check if rds is empty
					matched := Bool(true)
					when(!callremain(2)){
						state := Bits(IDLE)
						memvalid := Bool(false)
					}.elsewhen(callremain(2) && (funcmode(2) === Bits(1) || funcmode(2) === Bits(3))){
						val rdtentry = ((((funcarg0(1)>>addrshift)+totalfunccount(1)-Bits(1))&Bits(addrmask))<<1)(61,3)
						rdtcraddr := rdtentry&rdtcacheaddrmask
						
						memaddr := addrrdt+((((funcarg0(1)>>addrshift)+totalfunccount(1)-Bits(1))&Bits(addrmask))<<1)
						memtype := Bits(1)
						memtag := Bits(2)
						memw := Bool(true)
						memvalid := Bool(true)
						memdatain := id(2)
						
						state := Bits(WRITERDT)
						funccount := totalfunccount(1)
					}.otherwise{
						state := Bits(IDLE)
						memvalid := Bool(false)
					}
				}.otherwise{
					val rdtentry0 = (((funcretpointer(1)>>addrshift)&Bits(addrmask))<<1)(61,3)
					val rdtentry1 = ((((funcarg1(1)>>addrshift)+funccount-Bits(1))&Bits(addrmask))<<1)(61,3)
					val rdtentry2 = (((taraddr(1)>>addrshift)&Bits(addrmask))<<1)(61,3)
					
					when(brremain(1)){
						rdtcraddr := rdtentry0&rdtcacheaddrmask
					}.elsewhen(callremain(2)){
						rdtcraddr := rdtentry1&rdtcacheaddrmask
					}.otherwise{
						rdtcraddr := rdtentry2&rdtcacheaddrmask
					}
					rdtcprobing := Bool(true)
					
					when(brremain(1)){
						val realaddr = addrrdt+(((funcretpointer(1)>>addrshift)&Bits(addrmask))<<1)
						memaddr := realaddr(61,3)<<3
						memaddrmod := realaddr(2,0)
					}.elsewhen(callremain(2)){
						val realaddr = addrrdt+((((funcarg1(1)>>addrshift)+funccount-Bits(1))&Bits(addrmask))<<1)
						memaddr := realaddr(61,3)<<3
						memaddrmod := realaddr(2,0)
					}.otherwise{
						val realaddr = addrrdt+(((taraddr(1)>>addrshift)&Bits(addrmask))<<1)
						memaddr := realaddr(61,3)<<3
						memaddrmod := realaddr(2,0)
					}
					memtype := Bits(3)
					memtag := Bits(2)
					memw := Bool(false)
					memvalid := Bool(false)
					
					state := Bits(READRDT)
				}
			}.elsewhen(rdsmapcprobing && !cachehit){
				printf("rdsmap not hit, access rdt in memory\n")
				memvalid := Bool(true)
				rdsmapcprobing := Bool(false)
			}
			.otherwise{
				memvalid := Bool(false)
			}
		}.otherwise{
			when(io.mem.req.fire()){
				waitresp := Bool(true)
				memvalid := Bool(false)
			}.otherwise{
				memvalid := Bool(true)
			}
		}
	}
	.elsewhen(state === Bits(READRDT)){
		rdsp := rdss
		rdrdt_cycle := rdrdt_cycle + Bits(1)
		when(waitresp || rdtcprobing){
			val cachehit = rdtcdataout =/= Bits(0) && (rdtcdataout>>64) === ((memaddr-addrrdt)>>3)>>rdtcacheaddrwidth
			val memresp = io.mem.resp.valid && io.mem.resp.bits.tag(1,0) === Bits(2)
			when(cachehit || memresp){
				opld_count := opld_count + Bits(1)
				when(memresp){
					val rdtentry = ((memaddr-addrrdt)>>3)
					rdtcwaddr := rdtentry&rdtcacheaddrmask
					rdtcdatain := ((rdtentry>>rdtcacheaddrwidth)<<64)|io.mem.resp.bits.data
				}.otherwise{}
				
				waitresp := Bool(false)
				rdtcprobing := Bool(false)
				
				val previdfmem = (io.mem.resp.bits.data>>(memaddrmod(2,1)<<4))&Bits(0xffff)
				val previdfcache = (rdtcdataout>>(memaddrmod(2,1)<<4))&Bits(0xffff)
				
				when(waitresp){
					totalrdtcachemiss := totalrdtcachemiss + Bits(1)
					printf("latest write id: %d\n",previdfmem(15,0))
					prevwid := previdfmem(15,0)
				}.otherwise{
					totalrdtcachehit := totalrdtcachehit + Bits(1)
					printf("RDTCACHEHIT, rdtcache data: %x, tag: %x, latest write id: %d\n",rdtcdataout(63,0),(rdtcdataout>>64),previdfcache(15,0))
					prevwid := previdfcache
				}
				
				when(memresp && previdfmem === Bits(0) || cachehit && previdfcache === Bits(0)){//not need to check if rdt=0
					matched := Bool(true)
					when(!callremain(2)){//no other check
						state := Bits(IDLE)
						memvalid := Bool(false)
					}.elsewhen((callremain(2) && funccount <= Bits(1) && (funcmode(2) === Bits(1) || funcmode(2) === Bits(3)) )){//lib read finished, go to lib write
						val rdtentry = ((((funcarg0(1)>>addrshift)+totalfunccount(1)-Bits(1))&Bits(addrmask))<<1)(61,3)
						rdtcraddr := rdtentry&rdtcacheaddrmask
						
						memaddr := addrrdt+((((funcarg0(1)>>addrshift)+totalfunccount(1)-Bits(1))&Bits(addrmask))<<1)
						memtype := Bits(1)
						memtag := Bits(2)
						memw := Bool(true)
						memvalid := Bool(true)
						memdatain := id(2)
						
						state := Bits(WRITERDT)
						funccount := totalfunccount(1)
					}.elsewhen(callremain(2)){//lib read unfinished, continue
						val rdtentry = ((((funcarg1(1)>>addrshift)+funccount-Bits(1))&Bits(addrmask))<<1)(61,3)
						rdtcraddr := rdtentry&rdtcacheaddrmask
						rdtcprobing := Bool(true)
						
						val realaddr = addrrdt+((((funcarg1(1)>>addrshift)+funccount-Bits(1))&Bits(addrmask))<<1)
						memaddr := realaddr(61,3)<<3
						memaddrmod := realaddr(2,0)
						memtype := Bits(3)
						memtag := Bits(2)
						memw := Bool(false)
						memvalid := Bool(false)
						
						state := Bits(READRDT)
						funccount := funccount - Bits(1)
					}.otherwise{//OTW, finish DFI process
						state := Bits(IDLE)
						memvalid := Bool(false)
					}
				}.elsewhen(brremain(1)){//if stack pointer needs to be check
					matched := (memresp && previdfmem === Bits(0xffff)) || (cachehit && previdfcache === Bits(0xffff))
					brremain(1) := Bool(false)
					state := Bits(IDLE)//this is a function return, callremain must = 0
					memvalid := Bool(false)
				}.otherwise{
					val rdsentry = (rdss<<1)(61,3)
					rdscraddr := rdsentry&rdscacheaddrmask
					rdscprobing := Bool(true)
					
					memaddr := ((addrrds+(rdss<<1))(61,3))<<3
					memaddrmod := (addrrds+(rdss<<1))(2,0)
					memw := Bool(false)
					memtag := Bits(2)
					memtype := Bits(3)
					memvalid := Bool(false)
					
					state := Bits(READRDS)
				}
			}.elsewhen(rdtcprobing && !cachehit){
				printf("not hit, access rdt in memory\n")
				memvalid := Bool(true)
				rdtcprobing := Bool(false)
			}.otherwise{
				memvalid := Bool(false)
			}
		}.otherwise{
			when(io.mem.req.fire()){
				waitresp := Bool(true)
				printf("r addr: %x, id: %x, RDT entry: %x\n",memaddr,id(2),(memaddr-addrrdt)>>1)
				memvalid := Bool(false)
			}.otherwise{
				memvalid := Bool(true)
			}
		}
	}
	.elsewhen(state === Bits(READRDS)){
		rdrds_cycle := rdrds_cycle + Bits(1)
		when(waitresp || rdscprobing){
			val cachehit = rdscdataout =/= Bits(0) && (rdscdataout>>64) === ((memaddr-addrrds)>>3)>>rdscacheaddrwidth
			val memresp = io.mem.resp.valid && io.mem.resp.bits.tag(1,0) === Bits(2)
			when(cachehit || memresp){
				when(memresp){
					val rdsentry = ((memaddr-addrrds)>>3)
					rdscwaddr := rdsentry&rdscacheaddrmask
					rdscdatain := ((rdsentry>>rdscacheaddrwidth)<<64)|io.mem.resp.bits.data
				}.otherwise{}
				
				waitresp := Bool(false)
				rdscprobing := Bool(false)
				
				when(waitresp){
					printf("rds id: %d, %d, %d, %d\n",io.mem.resp.bits.data(15,0),io.mem.resp.bits.data(31,16),io.mem.resp.bits.data(47,32),io.mem.resp.bits.data(63,48))
					totalrdscachemiss := totalrdscachemiss + Bits(1)
				}.otherwise{
					printf("RDSCACHEHIT, rds id: %d, %d, %d, %d\n",rdscdataout(15,0),rdscdataout(31,16),rdscdataout(47,32),rdscdataout(63,48))
					totalrdscachehit := totalrdscachehit + Bits(1)
				}
				printf("mem side pass? %x, cache side pass? %x\n",onematched,conematched)
				rdsp:=rdsp+Bits(4)
				when((onematched && memresp) || (conematched && cachehit)){//check pass
					matched := Bool(true)
					when(!callremain(2)){//this is not a lib call check, then finish DFI
						state := Bits(IDLE)
						memvalid := Bool(false)
					}.elsewhen((callremain(2) && funccount <= Bits(1) && (funcmode(2) === Bits(1) || funcmode(2) === Bits(3)) )){//this is a call, finished, check if needs write
						val rdtentry = ((((funcarg0(1)>>addrshift)+totalfunccount(1)-Bits(1))&Bits(addrmask))<<1)(61,3)
						rdtcraddr := rdtentry&rdtcacheaddrmask
						
						memaddr := addrrdt+((((funcarg0(1)>>addrshift)+totalfunccount(1)-Bits(1))&Bits(addrmask))<<1)
						memtype := Bits(1)
						memtag := Bits(2)
						memw := Bool(true)
						memvalid := Bool(true)
						memdatain := id(2)
						
						state := Bits(WRITERDT)
						funccount := totalfunccount(1)
					}.elsewhen(callremain(2) && funccount <= Bits(1) && funcmode(2) === Bits(2)){
						state := Bits(IDLE)
						memvalid := Bool(false)
					}.elsewhen(callremain(2)){
						val rdtentry = ((((funcarg1(1)>>addrshift)+funccount-Bits(1))&Bits(addrmask))<<1)(61,3)
						rdtcraddr := rdtentry&rdtcacheaddrmask
						rdtcprobing := Bool(true)
						
						val realaddr = addrrdt+((((funcarg1(1)>>addrshift)+funccount-Bits(1))&Bits(addrmask))<<1)
						memaddr := realaddr(61,3)<<3
						memaddrmod := realaddr(2,0)
						memtype := Bits(3)
						memtag := Bits(2)
						memw := Bool(false)
						memvalid := Bool(false)
						
						state := Bits(READRDT)
						funccount := funccount - Bits(1)
					}.otherwise{
						state := Bits(IDLE)
						memvalid := Bool(false)
					}
				}.elsewhen(rdsp+Bits(4)>=rdse){//check not pass
					matched := Bool(false)
					when(!callremain(2)){
						state := Bits(IDLE)
						memvalid := Bool(false)
					}.elsewhen(callremain(2) && (funcmode(2) === Bits(1) || funcmode(2) === Bits(3))){//one of the data readed by lib is violated, no need to check the following data
						val rdtentry = ((((funcarg0(1)>>addrshift)+totalfunccount(1)-Bits(1))&Bits(addrmask))<<1)(61,3)
						rdtcraddr := rdtentry&rdtcacheaddrmask
						
						memaddr := addrrdt+((((funcarg0(1)>>addrshift)+totalfunccount(1)-Bits(1))&Bits(addrmask))<<1)
						memtype := Bits(1)
						memtag := Bits(2)
						memw := Bool(true)
						memvalid := Bool(true)
						memdatain := id(2)
						
						state := Bits(WRITERDT)
						funccount := totalfunccount(1)
					}.otherwise{
						state := Bits(IDLE)
						memvalid := Bool(false)
					}
				}.otherwise{
					val rdsentry = (rdsp+Bits(4)<<1)(61,3)
					rdscraddr := rdsentry&rdscacheaddrmask
					rdscprobing := Bool(true)
					
					memaddr := ((addrrds+(rdsp+Bits(4)<<1))(61,3))<<3
					memaddrmod := (addrrds+(rdsp+Bits(4)<<1))(2,0)
					memw := Bool(false)
					memtag := Bits(2)
					memtype := Bits(3)
					memvalid := Bool(false)
					
					state := Bits(READRDS)
				}
			}.elsewhen(rdscprobing && !cachehit){
				printf("not hit, access rds in memory\n")
				memvalid := Bool(true)
				rdscprobing := Bool(false)
			}.otherwise{
				memvalid := Bool(false)
			}
		}.otherwise{
			when(io.mem.req.fire()){
				printf("r rds: %x\n",((addrrds+(rdsp<<1))(61,3))<<3);
				memvalid := Bool(false)
				waitresp := Bool(true)
			}.otherwise{
				memvalid := Bool(true)
			}
		}
	}
	.elsewhen(state === Bits(WRITELDTRACE)){
		memtag := Bits(2)
		memaddr := addrdebug+Bits(800)+(rdtreaddebug_p<<3)
		memw := Bool(true)
		memdatain := taraddr(1)
		memtype := Bits(3)
		
		when(io.mem.req.fire()){
			printf("rdttrace write to addr: %x, trace %x\n",addrdebug+Bits(800)+(rdtreaddebug_p<<3),taraddr(1));
			when(rdtreaddebug_p>=Bits(0x7ff00)){
				rdtreaddebug_p := Bits(0)
			}.otherwise{
				rdtreaddebug_p := rdtreaddebug_p + Bits(1)
			}

			state := Bits(WAIT)
			
			val rdsmapentry = id(2)
			rdsmapcraddr := rdsmapentry&rdsmapcacheaddrmask
			rdsmapcprobing := Bool(true)
			
			memaddr := addrrds+(id(2)<<3)
			memw := Bool(false)
			memtype := Bits(3)
			memtag := Bits(2)
			memvalid := Bool(false)
			
			nstate := Bits(READRDSMAP)
			pstate := Bits(WRITELDTRACE)
		}.otherwise{
			memvalid := Bool(true)
		}
	}
	.elsewhen(state === Bits(REPORT)){
		memtag := Bits(2)
		memaddr := addrdebug+(count<<3)
		memw := Bool(true)
		when(count === Bits(0)){
			memdatain := violations
		}.elsewhen(count === Bits(1)){
			memdatain := errorcount
		}.elsewhen(count === Bits(2)){
			memdatain := totalcount
		}.elsewhen(count === Bits(3)){
			memdatain := funcarg0(1)
		}.elsewhen(count === Bits(4)){
			memdatain := funcarg1(1)
		}.elsewhen(count === Bits(5)){
			memdatain := funcarg2(1)
		}.elsewhen(count === Bits(6)){
			memdatain := funcretpointer(1)
		}.elsewhen(count === Bits(7)){
			memdatain := totalfunccount(1)
		}.elsewhen(count === Bits(8)){
			memdatain := opst_count
		}.elsewhen(count === Bits(9)){
			memdatain := opld_count
		}.elsewhen(count === Bits(10)){
			memdatain := uprdt_cycle
		}.elsewhen(count === Bits(11)){
			memdatain := rdrdt_cycle
		}.elsewhen(count === Bits(12)){
			memdatain := rdrdsmap_cycle
		}.elsewhen(count === Bits(13)){
			memdatain := rdrds_cycle
		}.elsewhen(count === Bits(14)){
			memdatain := chk_cycle
		}.elsewhen(count === Bits(15)){
			memdatain := maxlibarg0
		}.elsewhen(count === Bits(16)){
			memdatain := maxlibarg1
		}.elsewhen(count === Bits(17)){
			memdatain := maxlibarg2
		}.elsewhen(count === Bits(18)){
			memdatain := maxlibarg3
		}.elsewhen(count === Bits(19)){
			memdatain := maxlibretaddr
		}.elsewhen(count === Bits(20)){
			memdatain := maxlibretptr
		}.elsewhen(count === Bits(21)){
			memdatain := maxliblen
		}.elsewhen(count === Bits(22)){
			memdatain := totalldopt
		}.elsewhen(count === Bits(23)){
			memdatain := totalldnoopt
		}.elsewhen(count === Bits(24)){
			memdatain := totalrdtcachehit
		}.elsewhen(count === Bits(25)){
			memdatain := totalrdsmapcachehit
		}.elsewhen(count === Bits(26)){
			memdatain := rdtcdataout
		}.elsewhen(count === Bits(27)){
			memdatain := rdsmapcdataout
		}.elsewhen(count === Bits(28)){
			memdatain := totalrdscachehit
		}.elsewhen(count === Bits(29)){
			memdatain := totalrdtcachemiss
		}.elsewhen(count === Bits(30)){
			memdatain := totalrdsmapcachemiss
		}.elsewhen(count === Bits(31)){
			memdatain := totalrdscachemiss
		/*
		}.elsewhen(count === Bits(32)){
			memdatain := lat_call_count
		}.elsewhen(count === Bits(33)){
			memdatain := lat_call_cycles
		}.elsewhen(count === Bits(34)){
			memdatain := lat_libst_count
		}.elsewhen(count === Bits(35)){
			memdatain := lat_libst_cycles
		}.elsewhen(count === Bits(36)){
			memdatain := lat_libld_count
		}.elsewhen(count === Bits(37)){
			memdatain := lat_libld_cycles
		}.elsewhen(count === Bits(38)){
			memdatain := lat_libstld_count
		}.elsewhen(count === Bits(39)){
			memdatain := lat_libstld_cycles
		}.elsewhen(count === Bits(40)){
			memdatain := lat_ret_count
		}.elsewhen(count === Bits(41)){
			memdatain := lat_ret_cycles
		}.elsewhen(count === Bits(42)){
			memdatain := lat_st_count
		}.elsewhen(count === Bits(43)){
			memdatain := lat_st_cycles
		}.elsewhen(count === Bits(44)){
			memdatain := lat_ld_count
		}.elsewhen(count === Bits(45)){
			memdatain := lat_ld_cycles
		*/
		
		}.elsewhen(count === Bits(32)){
			memdatain := idle_cycle
		}.elsewhen(count === Bits(33)){
			memdatain := total_cycle
		}.elsewhen(count === Bits(34)){
			memdatain := fifocuspush_cycle
		}.elsewhen(count === Bits(35)){
			memdatain := fifofuncpush_cycle
		}.elsewhen(count === Bits(36)){
			memdatain := fifocusbranchpush
		}.elsewhen(count === Bits(37)){
			memdatain := fifocusbranchpop
		}.elsewhen(count === Bits(38)){
			memdatain := fifofuncbranchpush
		}.elsewhen(count === Bits(39)){
			memdatain := fifofuncbranchpop
		}.elsewhen(count === Bits(40)){
			memdatain := fifofuncloss
		}.elsewhen(count === Bits(41)){
			memdatain := fifofunclatestlossinfo
		}.elsewhen(count === Bits(42)){
			memdatain := fifofunclatestlosscuscount
		}.elsewhen(count === Bits(43)){
			memdatain := fifofunclatestlossfunccount
		}.elsewhen(count === Bits(44)){
			memdatain := fifofunclatestlossrecord
		}.elsewhen(count === Bits(45)){
			memdatain := fifofunclatestlosstaraddr
		
		}.elsewhen(count === Bits(46)){
			memdatain := fifocusmax
		}.elsewhen(count === Bits(47)){
			memdatain := fifofuncmax
		}.elsewhen(count === Bits(48)){
			memdatain := shdstackmax
		}.elsewhen(count === Bits(49)){
			memdatain := stop_call_count
		}.elsewhen(count === Bits(50)){
			memdatain := stop_call_count_all
		}.elsewhen(count === Bits(51)){
			memdatain := stop_call_count_max
		}.elsewhen(count === Bits(52)){
			memdatain := stop_coreexception
		}.elsewhen(count === Bits(53)){
			memdatain := totalstcount
		}.elsewhen(count === Bits(54)){
			memdatain := totalldcount
		}.elsewhen(count === Bits(55)){
			memdatain := totalcallcount
		}.elsewhen(count === Bits(56)){
			memdatain := totalcallexceptioncount
		}.elsewhen(count === Bits(57)){
			memdatain := Bits(0)
		}.elsewhen(count === Bits(58)){
			memdatain := Bits(0)
		}.elsewhen(count === Bits(59)){
			memdatain := Bits(0x11122233)
		}
		.otherwise{}
		
		memtype := Bits(3)
		when(io.mem.req.fire()){
			//printf("report write to addr: %x\n",addrdebug+((count+Bits(6))<<2));
			memvalid := Bool(false)
			state := Bits(WAIT)
			when(count < Bits(59)){
				nstate := Bits(REPORT)
			}.otherwise{
				nstate := Bits(IDLE)
			}
			pstate := Bits(REPORT)
			count := count + Bits(1)
		}.otherwise{
			memvalid := Bool(true)
		}
	}
	.otherwise{}
	
}
//flang----------------

class  AccumulatorExample(opcodes: OpcodeSet, val n: Int = 4)(implicit p: Parameters) extends LazyRoCC(opcodes) {
  override lazy val module = new AccumulatorExampleModuleImp(this)
}

class AccumulatorExampleModuleImp(outer: AccumulatorExample)(implicit p: Parameters) extends LazyRoCCModuleImp(outer)
    with HasCoreParameters {
  val regfile = Mem(outer.n, UInt(width = xLen))
  val busy = Reg(init = Vec.fill(outer.n){Bool(false)})

  val cmd = Queue(io.cmd)
  val funct = cmd.bits.inst.funct
  val addr = cmd.bits.rs2(log2Up(outer.n)-1,0)
  val doWrite = funct === UInt(0)
  val doRead = funct === UInt(1)
  val doLoad = funct === UInt(2)
  val doAccum = funct === UInt(3)
  val memRespTag = io.mem.resp.bits.tag(log2Up(outer.n)-1,0)

  // datapath
  val addend = cmd.bits.rs1
  val accum = regfile(addr)
  val wdata = Mux(doWrite, addend, accum + addend)

  when (cmd.fire() && (doWrite || doAccum)) {
    regfile(addr) := wdata
  }

  when (io.mem.resp.valid) {
    regfile(memRespTag) := io.mem.resp.bits.data
    busy(memRespTag) := Bool(false)
  }

  // control
  when (io.mem.req.fire()) {
    busy(addr) := Bool(true)
  }

  val doResp = cmd.bits.inst.xd
  val stallReg = busy(addr)
  val stallLoad = doLoad && !io.mem.req.ready
  val stallResp = doResp && !io.resp.ready

  cmd.ready := !stallReg && !stallLoad && !stallResp
    // command resolved if no stalls AND not issuing a load that will need a request

  // PROC RESPONSE INTERFACE
  io.resp.valid := cmd.valid && doResp && !stallReg && !stallLoad
    // valid response if valid command, need a response, and no stalls
  io.resp.bits.rd := cmd.bits.inst.rd
    // Must respond with the appropriate tag or undefined behavior
  io.resp.bits.data := accum
    // Semantics is to always send out prior accumulator register value

  io.busy := cmd.valid || busy.reduce(_||_)
    // Be busy when have pending memory requests or committed possibility of pending requests
  io.interrupt := Bool(false)
    // Set this true to trigger an interrupt on the processor (please refer to supervisor documentation)

  // MEMORY REQUEST INTERFACE
  io.mem.req.valid := cmd.valid && doLoad && !stallReg && !stallResp
  io.mem.req.bits.addr := addend
  io.mem.req.bits.tag := addr
  io.mem.req.bits.cmd := M_XRD // perform a load (M_XWR for stores)
  io.mem.req.bits.typ := MT_D // D = 8 bytes, W = 4, H = 2, B = 1
  io.mem.req.bits.data := Bits(0) // we're not performing any stores...
  io.mem.req.bits.phys := Bool(false)
}

class  TranslatorExample(opcodes: OpcodeSet)(implicit p: Parameters) extends LazyRoCC(opcodes, nPTWPorts = 1) {
  override lazy val module = new TranslatorExampleModuleImp(this)
}

class TranslatorExampleModuleImp(outer: TranslatorExample)(implicit p: Parameters) extends LazyRoCCModuleImp(outer)
    with HasCoreParameters {
  val req_addr = Reg(UInt(width = coreMaxAddrBits))
  val req_rd = Reg(io.resp.bits.rd)
  val req_offset = req_addr(pgIdxBits - 1, 0)
  val req_vpn = req_addr(coreMaxAddrBits - 1, pgIdxBits)
  val pte = Reg(new PTE)

  val s_idle :: s_ptw_req :: s_ptw_resp :: s_resp :: Nil = Enum(Bits(), 4)
  val state = Reg(init = s_idle)

  io.cmd.ready := (state === s_idle)

  when (io.cmd.fire()) {
    req_rd := io.cmd.bits.inst.rd
    req_addr := io.cmd.bits.rs1
    state := s_ptw_req
  }

  private val ptw = io.ptw(0)

  when (ptw.req.fire()) { state := s_ptw_resp }

  when (state === s_ptw_resp && ptw.resp.valid) {
    pte := ptw.resp.bits.pte
    state := s_resp
  }

  when (io.resp.fire()) { state := s_idle }

  ptw.req.valid := (state === s_ptw_req)
  ptw.req.bits.valid := true.B
  ptw.req.bits.bits.addr := req_vpn

  io.resp.valid := (state === s_resp)
  io.resp.bits.rd := req_rd
  io.resp.bits.data := Mux(pte.leaf(), Cat(pte.ppn, req_offset), SInt(-1, xLen).asUInt)

  io.busy := (state =/= s_idle)
  io.interrupt := Bool(false)
  io.mem.req.valid := Bool(false)
}

class  CharacterCountExample(opcodes: OpcodeSet)(implicit p: Parameters) extends LazyRoCC(opcodes) {
  override lazy val module = new CharacterCountExampleModuleImp(this)
  override val atlNode = TLClientNode(Seq(TLClientPortParameters(Seq(TLClientParameters("CharacterCountRoCC")))))
}

class CharacterCountExampleModuleImp(outer: CharacterCountExample)(implicit p: Parameters) extends LazyRoCCModuleImp(outer)
  with HasCoreParameters
  with HasL1CacheParameters {
  val cacheParams = tileParams.icache.get

  private val blockOffset = blockOffBits
  private val beatOffset = log2Up(cacheDataBits/8)

  val needle = Reg(UInt(width = 8))
  val addr = Reg(UInt(width = coreMaxAddrBits))
  val count = Reg(UInt(width = xLen))
  val resp_rd = Reg(io.resp.bits.rd)

  val addr_block = addr(coreMaxAddrBits - 1, blockOffset)
  val offset = addr(blockOffset - 1, 0)
  val next_addr = (addr_block + UInt(1)) << UInt(blockOffset)

  val s_idle :: s_acq :: s_gnt :: s_check :: s_resp :: Nil = Enum(Bits(), 5)
  val state = Reg(init = s_idle)

  val (tl_out, edgesOut) = outer.atlNode.out(0)
  val gnt = tl_out.d.bits
  val recv_data = Reg(UInt(width = cacheDataBits))
  val recv_beat = Reg(UInt(width = log2Up(cacheDataBeats+1)), init = UInt(0))

  val data_bytes = Vec.tabulate(cacheDataBits/8) { i => recv_data(8 * (i + 1) - 1, 8 * i) }
  val zero_match = data_bytes.map(_ === UInt(0))
  val needle_match = data_bytes.map(_ === needle)
  val first_zero = PriorityEncoder(zero_match)

  val chars_found = PopCount(needle_match.zipWithIndex.map {
    case (matches, i) =>
      val idx = Cat(recv_beat - UInt(1), UInt(i, beatOffset))
      matches && idx >= offset && UInt(i) <= first_zero
  })
  val zero_found = zero_match.reduce(_ || _)
  val finished = Reg(Bool())

  io.cmd.ready := (state === s_idle)
  io.resp.valid := (state === s_resp)
  io.resp.bits.rd := resp_rd
  io.resp.bits.data := count
  tl_out.a.valid := (state === s_acq)
  tl_out.a.bits := edgesOut.Get(
                       fromSource = UInt(0),
                       toAddress = addr_block << blockOffset,
                       lgSize = UInt(lgCacheBlockBytes))._2
  tl_out.d.ready := (state === s_gnt)

  when (io.cmd.fire()) {
    addr := io.cmd.bits.rs1
    needle := io.cmd.bits.rs2
    resp_rd := io.cmd.bits.inst.rd
    count := UInt(0)
    finished := Bool(false)
    state := s_acq
  }

  when (tl_out.a.fire()) { state := s_gnt }

  when (tl_out.d.fire()) {
    recv_beat := recv_beat + UInt(1)
    recv_data := gnt.data
    state := s_check
  }

  when (state === s_check) {
    when (!finished) {
      count := count + chars_found
    }
    when (zero_found) { finished := Bool(true) }
    when (recv_beat === UInt(cacheDataBeats)) {
      addr := next_addr
      state := Mux(zero_found || finished, s_resp, s_acq)
    } .otherwise {
      state := s_gnt
    }
  }

  when (io.resp.fire()) { state := s_idle }

  io.busy := (state =/= s_idle)
  io.interrupt := Bool(false)
  io.mem.req.valid := Bool(false)
  // Tie off unused channels
  tl_out.b.ready := Bool(true)
  tl_out.c.valid := Bool(false)
  tl_out.e.valid := Bool(false)
}

class OpcodeSet(val opcodes: Seq[UInt]) {
  def |(set: OpcodeSet) =
    new OpcodeSet(this.opcodes ++ set.opcodes)

  def matches(oc: UInt) = opcodes.map(_ === oc).reduce(_ || _)
}

object OpcodeSet {
  def custom0 = new OpcodeSet(Seq(Bits("b0001011")))
  def custom1 = new OpcodeSet(Seq(Bits("b0101011")))
  def custom2 = new OpcodeSet(Seq(Bits("b1011011")))
  def custom3 = new OpcodeSet(Seq(Bits("b1111011")))
  def all = custom0 | custom1 | custom2 | custom3
}

class RoccCommandRouter(opcodes: Seq[OpcodeSet])(implicit p: Parameters)
    extends CoreModule()(p) {
  val io = new Bundle {
    val in = Decoupled(new RoCCCommand).flip
    val out = Vec(opcodes.size, Decoupled(new RoCCCommand))
    val busy = Bool(OUTPUT)
  }

  val cmd = Queue(io.in)
  val cmdReadys = io.out.zip(opcodes).map { case (out, opcode) =>
    val me = opcode.matches(cmd.bits.inst.opcode)
    out.valid := cmd.valid && me
    out.bits := cmd.bits
    out.ready && me
  }
  cmd.ready := cmdReadys.reduce(_ || _)
  io.busy := cmd.valid

  assert(PopCount(cmdReadys) <= UInt(1),
    "Custom opcode matched for more than one accelerator")
}
