// See LICENSE for license details.
package sifive.fpgashells.shell.xilinx

import chisel3._
import chisel3.experimental.{attach, Analog, IO, withClockAndReset}
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.SyncResetSynchronizerShiftReg
import sifive.fpgashells.clocks._
import sifive.fpgashells.shell._
import sifive.fpgashells.ip.xilinx._
import sifive.blocks.devices.chiplink._
import sifive.fpgashells.devices.xilinx.xilinxxcvu440mig._
import sifive.fpgashells.devices.xilinx.xdma._

class SysClockXCVU440Overlay(val shell: XCVU440Shell, val name: String, params: ClockInputOverlayParams)
  extends LVDSClockInputXilinxOverlay(params)
{
  val node = shell { ClockSourceNode(freqMHz = 250, jitterPS = 50)(ValName(name)) }

  shell { InModuleBody {
    shell.xdc.addPackagePin(io.p, "AG47")
    shell.xdc.addPackagePin(io.n, "AF47")
    shell.xdc.addIOStandard(io.p, "DIFF_SSTL15_DCI")
    shell.xdc.addIOStandard(io.n, "DIFF_SSTL15_DCI")
  } }
}

class SDIOXCVU440Overlay(val shell: XCVU440Shell, val name: String, params: SDIOOverlayParams)
  extends SDIOXilinxOverlay(params)
{
  shell { InModuleBody {
    val packagePinsWithPackageIOs = Seq(("M19", IOPin(io.sdio_clk)),
                                        ("T19", IOPin(io.sdio_cmd)),
                                        ("R18", IOPin(io.sdio_dat_0)),
                                        ("T17", IOPin(io.sdio_dat_1)),
                                        ("T18", IOPin(io.sdio_dat_2)),
                                        ("L19", IOPin(io.sdio_dat_3)))

    packagePinsWithPackageIOs foreach { case (pin, io) => {
      shell.xdc.addPackagePin(io, pin)
      shell.xdc.addIOStandard(io, "LVCMOS18")
      shell.xdc.addIOB(io)
    } }
    packagePinsWithPackageIOs drop 1 foreach { case (pin, io) => {
      shell.xdc.addPullup(io)
    } }
  } }
}

class UARTXCVU440Overlay(val shell: XCVU440Shell, val name: String, params: UARTOverlayParams)
  extends UARTXilinxOverlay(params)
{
  shell { InModuleBody {
    val packagePinsWithPackageIOs = Seq(("R16", IOPin(io.ctsn)),
                                        ("P14", IOPin(io.rtsn)),
                                        ("F19", IOPin(io.rxd)),
                                        ("F20", IOPin(io.txd)))

    // val packagePinsWithPackageIOs = Seq(("F19", IOPin(io.rxd)),
    //                                     ("F20", IOPin(io.txd)))

    packagePinsWithPackageIOs foreach { case (pin, io) => {
      shell.xdc.addPackagePin(io, pin)
      shell.xdc.addIOStandard(io, "LVCMOS18")
      shell.xdc.addIOB(io)
    } }
  } }
}

class LEDXCVU440Overlay(val shell: XCVU440Shell, val name: String, params: LEDOverlayParams)
  extends LEDXilinxOverlay(params, packagePins = Seq("N18", "T20", "P18", "E21", "P20", "F17", "F18", "BA37"))
{
  shell { InModuleBody {
    IOPin.of(io).foreach { shell.xdc.addIOStandard(_, "LVCMOS18") }
  } }
}

class SwitchXCVU440Overlay(val shell: XCVU440Shell, val name: String, params: SwitchOverlayParams)
  extends SwitchXilinxOverlay(params, packagePins = Seq("J21", "H19", "G21", "H18"))
{
  shell { InModuleBody {
    IOPin.of(io).foreach { shell.xdc.addIOStandard(_, "LVCMOS18") }
  } }
}

class ChipLinkXCVU440Overlay(val shell: XCVU440Shell, val name: String, params: ChipLinkOverlayParams)
  extends ChipLinkXilinxOverlay(params, rxPhase= -120, txPhase= -90, rxMargin=0.6, txMargin=0.5)
{
  val ereset_n = shell { InModuleBody {
    val ereset_n = IO(Analog(1.W))
    ereset_n.suggestName("ereset_n")
    val pin = IOPin(ereset_n, 0)
    shell.xdc.addPackagePin(pin, "BC8")
    shell.xdc.addIOStandard(pin, "LVCMOS18")
    shell.xdc.addTermination(pin, "NONE")
    shell.xdc.addPullup(pin)

    val iobuf = Module(new IOBUF)
    iobuf.suggestName("chiplink_ereset_iobuf")
    attach(ereset_n, iobuf.io.IO)
    iobuf.io.T := true.B // !oe
    iobuf.io.I := false.B

    iobuf.io.O
  } }

  shell { InModuleBody {
    val dir1 = Seq("BC9", "AV8", "AV9", /* clk, rst, send */
                   "AY9",  "BA9",  "BF10", "BF9",  "BC11", "BD11", "BD12", "BE12",
                   "BF12", "BF11", "BE14", "BF14", "BD13", "BE13", "BC15", "BD15",
                   "BE15", "BF15", "BA14", "BB14", "BB13", "BB12", "BA16", "BA15",
                   "BC14", "BC13", "AY8",  "AY7",  "AW8",  "AW7",  "BB16", "BC16")
    val dir2 = Seq("AV14", "AK13", "AK14", /* clk, rst, send */
                   "AR14", "AT14", "AP12", "AR12", "AW12", "AY12", "AW11", "AY10",
                   "AU11", "AV11", "AW13", "AY13", "AN16", "AP16", "AP13", "AR13",
                   "AT12", "AU12", "AK15", "AL15", "AL14", "AM14", "AV10", "AW10",
                   "AN15", "AP15", "AK12", "AL12", "AM13", "AM12", "AJ13", "AJ12")
    (IOPin.of(io.b2c) zip dir1) foreach { case (io, pin) => shell.xdc.addPackagePin(io, pin) }
    (IOPin.of(io.c2b) zip dir2) foreach { case (io, pin) => shell.xdc.addPackagePin(io, pin) }
  } }
}

// TODO: JTAG is untested
class JTAGDebugXCVU440Overlay(val shell: XCVU440Shell, val name: String, params: JTAGDebugOverlayParams)
  extends JTAGDebugXilinxOverlay(params)
{
  shell { InModuleBody {
    shell.sdc.addClock("JTCK", IOPin(io.jtag_TCK), 10)
    shell.sdc.addGroup(clocks = Seq("JTCK"))
    shell.xdc.clockDedicatedRouteFalse(IOPin(io.jtag_TCK))
    val packagePinsWithPackageIOs = Seq(("F29", IOPin(io.jtag_TCK)),
                                        ("F28", IOPin(io.jtag_TMS)),
                                        ("M29", IOPin(io.jtag_TDI)),
                                        ("L29", IOPin(io.jtag_TDO)))

    packagePinsWithPackageIOs foreach { case (pin, io) => {
      shell.xdc.addPackagePin(io, pin)
      shell.xdc.addIOStandard(io, "LVCMOS18")
      shell.xdc.addPullup(io)
    } }
  } }
}

case object XCVU440DDRSize extends Field[BigInt](0x40000000L * 2) // 2GB
class DDRXCVU440Overlay(val shell: XCVU440Shell, val name: String, params: DDROverlayParams)
  extends DDROverlay[XilinxXCVU440MIGPads](params)
{
  val size = p(XCVU440DDRSize)

  val migParams = XilinxXCVU440MIGParams(address = AddressSet.misaligned(params.baseAddress, size))
  val mig = LazyModule(new XilinxXCVU440MIG(migParams))
  val ioNode = BundleBridgeSource(() => mig.module.io.cloneType)
  val topIONode = shell { ioNode.makeSink() }
  val ddrUI     = shell { ClockSourceNode(freqMHz = 200) }
  val areset    = shell { ClockSinkNode(Seq(ClockSinkParameters())) }
  areset := params.wrangler := ddrUI

  def designOutput = mig.node
  def ioFactory = new XilinxXCVU440MIGPads(size)

  InModuleBody { ioNode.bundle <> mig.module.io }

  shell { InModuleBody {
    require (shell.sys_clock.isDefined, "Use of DDRXCVU440Overlay depends on SysClockXCVU440Overlay")
    val (sys, _) = shell.sys_clock.get.node.out(0)
    val (ui, _) = ddrUI.out(0)
    val (ar, _) = areset.in(0)
    val port = topIONode.bundle.port
    io <> port
    ui.clock := port.c0_ddr3_ui_clk
    ui.reset := /*!port.mmcm_locked ||*/ port.c0_ddr3_ui_clk_sync_rst
    port.c0_sys_clk_i := sys.clock.asUInt
    port.sys_rst := sys.reset // pllReset
    port.c0_ddr3_aresetn := !ar.reset

    val allddrpins = Seq(  "AG49", "AF44", "AG50", "AE45", "AF50", "AD50", "AF45",
      "AE50", "AD49", "AE42", "AH48", "AE51", "AE46", "AG42", "AD48", "AE43", "AH47",
      "AG46", "AD45", "AG45", "AG44", "AH46", "R50", "AE47", "AE48", "AD44", "AH44",
      "AC44", "AB47", "AC51", "AD53",  "W53", "W51", "U47",  "V43",  "AH43", "AB41",
      "Y41", "AC43", "AB42", "W43", "Y42", "AC42", "Y43", "W45", "W44", "AC47",
      "AC46", "AA45", "AA44", "W46", "Y46", "Y47", "AB50", "AA48", "AA49", "Y50",
      "Y48", "AA50", "Y51", "AB52", "Y54", "AC52", "AC54", "Y52", "AA52", "AB54",
      "AA54", "R52", "T53", "V53", "V54", "U52", "V52", "R51", "T52", "U50",
      "W50", "W49", "U49", "T49", "T48", "V49", "T50", "R45", "R46", "R48",
      "R47", "U46", "U45", "U44", "V46", "T42", "T43", "U42", "V42", "T40",
      "R41", "U40", "R40", "AA43", "AB46", "AB49", "Y53", "T54", "V48", "T45",
      "R43", "AA42", "AB45", "AC49", "AA53", "U54", "W48", "T44", "R42")

    (IOPin.of(io) zip allddrpins) foreach { case (io, pin) => shell.xdc.addPackagePin(io, pin) }
  } }

  shell.sdc.addGroup(pins = Seq(mig.island.module.blackbox.io.c0_ddr3_ui_clk))
}

class PCIeXCVU440FMCOverlay(val shell: XCVU440Shell, val name: String, params: PCIeOverlayParams)
  extends PCIeUltraScaleOverlay(XDMAParams(
    name     = "fmc_xdma",
    location = "X0Y3",
    bars     = params.bars,
    control  = params.ecam,
    lanes    = 4), params)
{
  shell { InModuleBody {
    // Work-around incorrectly pre-assigned pins
    IOPin.of(io).foreach { shell.xdc.addPackagePin(_, "") }

    // We need some way to connect both of these to reach x8
    val ref126 = Seq("V38",  "V39")  /* [pn] GBT0 Bank 126 */
    val ref121 = Seq("AK38", "AK39") /* [pn] GBT0 Bank 121 */
    val ref = ref126

    // Bank 126 (DP5, DP6, DP4, DP7), Bank 121 (DP3, DP2, DP1, DP0)
    val rxp = Seq("U45", "R45", "W45", "N45", "AJ45", "AL45", "AN45", "AR45") /* [0-7] */
    val rxn = Seq("U46", "R46", "W46", "N46", "AJ46", "AL46", "AN46", "AR46") /* [0-7] */
    val txp = Seq("P42", "M42", "T42", "K42", "AL40", "AM42", "AP42", "AT42") /* [0-7] */
    val txn = Seq("P43", "M43", "T43", "K43", "AL41", "AM43", "AP43", "AT43") /* [0-7] */

    def bind(io: Seq[IOPin], pad: Seq[String]) {
      (io zip pad) foreach { case (io, pad) => shell.xdc.addPackagePin(io, pad) }
    }

    bind(IOPin.of(io.refclk), ref)
    // We do these individually so that zip falls off the end of the lanes:
    bind(IOPin.of(io.lanes.pci_exp_txp), txp)
    bind(IOPin.of(io.lanes.pci_exp_txn), txn)
    bind(IOPin.of(io.lanes.pci_exp_rxp), rxp)
    bind(IOPin.of(io.lanes.pci_exp_rxn), rxn)
  } }
}

class PCIeXCVU440EdgeOverlay(val shell: XCVU440Shell, val name: String, params: PCIeOverlayParams)
  extends PCIeUltraScaleOverlay(XDMAParams(
    name     = "edge_xdma",
    location = "X1Y2",
    bars     = params.bars,
    control  = params.ecam,
    lanes    = 8), params)
{
  shell { InModuleBody {
    // Work-around incorrectly pre-assigned pins
    IOPin.of(io).foreach { shell.xdc.addPackagePin(_, "") }

    // PCIe Edge connector U2
    //   Lanes 00-03 Bank 227
    //   Lanes 04-07 Bank 226
    //   Lanes 08-11 Bank 225
    //   Lanes 12-15 Bank 224

    // FMC+ J22
    val ref227 = Seq("AC9", "AC8")  /* [pn]  Bank 227 PCIE_CLK2_*/
    val ref = ref227

    // PCIe Edge connector U2 : Bank 227, 226
    val rxp = Seq("AA4", "AB2", "AC4", "AD2", "AE4", "AF2", "AG4", "AH2") // [0-7]
    val rxn = Seq("AA3", "AB1", "AC3", "AD1", "AE3", "AF1", "AG3", "AH1") // [0-7]
    val txp = Seq("Y7", "AB7", "AD7", "AF7", "AH7", "AK7", "AM7", "AN5") // [0-7]
    val txn = Seq("Y6", "AB6", "AD6", "AF6", "AH6", "AK6", "AM6", "AN4") // [0-7]

    def bind(io: Seq[IOPin], pad: Seq[String]) {
      (io zip pad) foreach { case (io, pad) => shell.xdc.addPackagePin(io, pad) }
    }

    bind(IOPin.of(io.refclk), ref)
    // We do these individually so that zip falls off the end of the lanes:
    bind(IOPin.of(io.lanes.pci_exp_txp), txp)
    bind(IOPin.of(io.lanes.pci_exp_txn), txn)
    bind(IOPin.of(io.lanes.pci_exp_rxp), rxp)
    bind(IOPin.of(io.lanes.pci_exp_rxn), rxn)
  } }
}

class XCVU440Shell()(implicit p: Parameters) extends UltraScaleShell
{
  // PLL reset causes
  val pllReset = InModuleBody { Wire(Bool()) }

  // Order matters; ddr depends on sys_clock
  val sys_clock = Overlay(ClockInputOverlayKey)(new SysClockXCVU440Overlay (_, _, _))
  val led       = Overlay(LEDOverlayKey)       (new LEDXCVU440Overlay      (_, _, _))
  val switch    = Overlay(SwitchOverlayKey)    (new SwitchXCVU440Overlay   (_, _, _))
  val chiplink  = Overlay(ChipLinkOverlayKey)  (new ChipLinkXCVU440Overlay (_, _, _))
  val ddr       = Overlay(DDROverlayKey)       (new DDRXCVU440Overlay      (_, _, _))
  // val fmc       = Overlay(PCIeOverlayKey)      (new PCIeXCVU440FMCOverlay  (_, _, _))
  // val edge      = Overlay(PCIeOverlayKey)      (new PCIeXCVU440EdgeOverlay (_, _, _))
  val uart      = Overlay(UARTOverlayKey)      (new UARTXCVU440Overlay     (_, _, _))
  val sdio      = Overlay(SDIOOverlayKey)      (new SDIOXCVU440Overlay     (_, _, _))
  val jtag      = Overlay(JTAGDebugOverlayKey) (new JTAGDebugXCVU440Overlay(_, _, _))

  val topDesign = LazyModule(p(DesignKey)(designParameters))

  // Place the sys_clock at the Shell if the user didn't ask for it
  p(ClockInputOverlayKey).foreach(_(ClockInputOverlayParams()))

  override lazy val module = new LazyRawModuleImp(this) {
    val reset = IO(Input(Bool()))
    xdc.addPackagePin(reset, "BD28")
    xdc.addIOStandard(reset, "LVCMOS18")

    val reset_ibuf = Module(new IBUF)
    reset_ibuf.io.I := reset

    val powerOnReset = PowerOnResetFPGAOnly(sys_clock.get.clock)
    sdc.addAsyncPath(Seq(powerOnReset))

    pllReset :=
      (~reset_ibuf.io.O) || powerOnReset ||
      chiplink.map(!_.ereset_n).getOrElse(false.B)
  }
}
