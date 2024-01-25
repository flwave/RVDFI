// See LICENSE for license details.
package sifive.fpgashells.devices.xilinx.xilinxxcvu440mig

import Chisel._
import freechips.rocketchip.config._
import freechips.rocketchip.subsystem.BaseSubsystem
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp, AddressRange}

case object MemoryXilinxDDRKey extends Field[XilinxXCVU440MIGParams]

trait HasMemoryXilinxXCVU440MIG { this: BaseSubsystem =>
  val module: HasMemoryXilinxXCVU440MIGModuleImp

  val xilinxxcvu440mig = LazyModule(new XilinxXCVU440MIG(p(MemoryXilinxDDRKey)))

  xilinxxcvu440mig.node := mbus.toDRAMController(Some("xilinxxcvu440mig"))()
}

trait HasMemoryXilinxXCVU440MIGBundle {
  val xilinxxcvu440mig: XilinxXCVU440MIGIO
  def connectXilinxXCVU440MIGToPads(pads: XilinxXCVU440MIGPads) {
    pads <> xilinxxcvu440mig
  }
}

trait HasMemoryXilinxXCVU440MIGModuleImp extends LazyModuleImp
    with HasMemoryXilinxXCVU440MIGBundle {
  val outer: HasMemoryXilinxXCVU440MIG
  val ranges = AddressRange.fromSets(p(MemoryXilinxDDRKey).address)
  require (ranges.size == 1, "DDR range must be contiguous")
  val depth = ranges.head.size
  val xilinxxcvu440mig = IO(new XilinxXCVU440MIGIO(depth))

  xilinxxcvu440mig <> outer.xilinxxcvu440mig.module.io.port
}
