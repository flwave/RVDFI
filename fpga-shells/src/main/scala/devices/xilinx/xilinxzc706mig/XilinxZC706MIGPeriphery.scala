// See LICENSE for license details.
package sifive.fpgashells.devices.xilinx.xilinxzc706mig

import Chisel._
import freechips.rocketchip.config._
import freechips.rocketchip.subsystem.BaseSubsystem
import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp, AddressRange}

case object MemoryXilinxDDRKey extends Field[XilinxZC706MIGParams]

trait HasMemoryXilinxZC706MIG { this: BaseSubsystem =>
  val module: HasMemoryXilinxZC706MIGModuleImp

  val xilinxzc706mig = LazyModule(new XilinxZC706MIG(p(MemoryXilinxDDRKey)))

  xilinxzc706mig.node := mbus.toDRAMController(Some("xilinxzc706mig"))()
}

trait HasMemoryXilinxZC706MIGBundle {
  val xilinxzc706mig: XilinxZC706MIGIO
  def connectXilinxZC706MIGToPads(pads: XilinxZC706MIGPads) {
    pads <> xilinxzc706mig
  }
}

trait HasMemoryXilinxZC706MIGModuleImp extends LazyModuleImp
    with HasMemoryXilinxZC706MIGBundle {
  val outer: HasMemoryXilinxZC706MIG
  val ranges = AddressRange.fromSets(p(MemoryXilinxDDRKey).address)
  require (ranges.size == 1, "DDR range must be contiguous")
  val depth = ranges.head.size
  val xilinxzc706mig = IO(new XilinxZC706MIGIO(depth))

  xilinxzc706mig <> outer.xilinxzc706mig.module.io.port
}
