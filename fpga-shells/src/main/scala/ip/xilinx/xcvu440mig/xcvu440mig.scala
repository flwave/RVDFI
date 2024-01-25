// See LICENSE for license details.
package sifive.fpgashells.ip.xilinx.xcvu440mig

import Chisel._
import chisel3.experimental.{Analog,attach}
import freechips.rocketchip.util.{ElaborationArtefacts}
import freechips.rocketchip.util.GenericParameterizedBundle
import freechips.rocketchip.config._

// IP VLNV: xilinx.com:customize_ip:xcvu440mig:1.0
// Black Box

class XCVU440MIGIODDR(depth : BigInt) extends GenericParameterizedBundle(depth) {
  require((depth<=0x80000000L),"XCVU440MIGIODDR supports upto 2GB depth configuraton")
  // val c0_ddr3_adr           = Bits(OUTPUT,16)
  // val c0_ddr3_bg            = Bits(OUTPUT,1)
  // val c0_ddr3_ba            = Bits(OUTPUT,3)
  // val c0_ddr3_reset_n       = Bool(OUTPUT)
  // val c0_ddr3_act_n         = Bool(OUTPUT)
  // val c0_ddr3_ck_c          = Bits(OUTPUT,1)
  // val c0_ddr3_ck_t          = Bits(OUTPUT,1)
  // val c0_ddr3_cke           = Bits(OUTPUT,1)
  // val c0_ddr3_cs_n          = Bits(OUTPUT,1)
  // val c0_ddr3_odt           = Bits(OUTPUT,1)

  // val c0_ddr3_dq            = Analog(64.W)
  // val c0_ddr3_dqs_c         = Analog(8.W)
  // val c0_ddr3_dqs_t         = Analog(8.W)
  // val c0_ddr3_dm_dbi_n      = Analog(8.W)

  val c0_ddr3_addr             = Bits(OUTPUT,16)
  val c0_ddr3_ba               = Bits(OUTPUT,3)
  val c0_ddr3_ras_n            = Bool(OUTPUT)
  val c0_ddr3_cas_n            = Bool(OUTPUT)
  val c0_ddr3_we_n             = Bool(OUTPUT)
  val c0_ddr3_reset_n          = Bool(OUTPUT)
  val c0_ddr3_ck_p             = Bits(OUTPUT,1)
  val c0_ddr3_ck_n             = Bits(OUTPUT,1)
  val c0_ddr3_cke              = Bits(OUTPUT,1)
  val c0_ddr3_cs_n             = Bits(OUTPUT,1)
  val c0_ddr3_dm               = Bits(OUTPUT,8)
  val c0_ddr3_odt              = Bits(OUTPUT,1)

  val c0_ddr3_dq               = Analog(64.W)
  val c0_ddr3_dqs_n            = Analog(8.W)
  val c0_ddr3_dqs_p            = Analog(8.W)
}

//reused directly in io bundle for sifive.blocks.devices.xilinxxcvu440mig
trait XCVU440MIGIOClocksReset extends Bundle {
  //inputs
  //"NO_BUFFER" clock source (must be connected to IBUF outside of IP)
  val c0_sys_clk_i              = Bool(INPUT)
  //user interface signals
  val c0_ddr3_ui_clk            = Clock(OUTPUT)
  val c0_ddr3_ui_clk_sync_rst   = Bool(OUTPUT)
  val c0_ddr3_aresetn           = Bool(INPUT)
  //misc
  val c0_init_calib_complete    = Bool(OUTPUT)
  val sys_rst                   = Bool(INPUT)
}

//scalastyle:off
//turn off linter: blackbox name must match verilog module
class xcvu440mig(depth : BigInt)(implicit val p:Parameters) extends BlackBox
{
  require((depth<=0x80000000L),"xcvu440mig supports upto 2GB depth configuraton")

  val io = new XCVU440MIGIODDR(depth) with XCVU440MIGIOClocksReset {
    //slave interface write address ports
    val c0_ddr3_s_axi_awid            = Bits(INPUT,4)
    val c0_ddr3_s_axi_awaddr          = Bits(INPUT,31)
    val c0_ddr3_s_axi_awlen           = Bits(INPUT,8)
    val c0_ddr3_s_axi_awsize          = Bits(INPUT,3)
    val c0_ddr3_s_axi_awburst         = Bits(INPUT,2)
    val c0_ddr3_s_axi_awlock          = Bits(INPUT,1)
    val c0_ddr3_s_axi_awcache         = Bits(INPUT,4)
    val c0_ddr3_s_axi_awprot          = Bits(INPUT,3)
    val c0_ddr3_s_axi_awqos           = Bits(INPUT,4)
    val c0_ddr3_s_axi_awvalid         = Bool(INPUT)
    val c0_ddr3_s_axi_awready         = Bool(OUTPUT)
    //slave interface write data ports
    val c0_ddr3_s_axi_wdata           = Bits(INPUT,64)
    val c0_ddr3_s_axi_wstrb           = Bits(INPUT,8)
    val c0_ddr3_s_axi_wlast           = Bool(INPUT)
    val c0_ddr3_s_axi_wvalid          = Bool(INPUT)
    val c0_ddr3_s_axi_wready          = Bool(OUTPUT)
    //slave interface write response ports
    val c0_ddr3_s_axi_bready          = Bool(INPUT)
    val c0_ddr3_s_axi_bid             = Bits(OUTPUT,4)
    val c0_ddr3_s_axi_bresp           = Bits(OUTPUT,2)
    val c0_ddr3_s_axi_bvalid          = Bool(OUTPUT)
    //slave interface read address ports
    val c0_ddr3_s_axi_arid            = Bits(INPUT,4)
    val c0_ddr3_s_axi_araddr          = Bits(INPUT,31)
    val c0_ddr3_s_axi_arlen           = Bits(INPUT,8)
    val c0_ddr3_s_axi_arsize          = Bits(INPUT,3)
    val c0_ddr3_s_axi_arburst         = Bits(INPUT,2)
    val c0_ddr3_s_axi_arlock          = Bits(INPUT,1)
    val c0_ddr3_s_axi_arcache         = Bits(INPUT,4)
    val c0_ddr3_s_axi_arprot          = Bits(INPUT,3)
    val c0_ddr3_s_axi_arqos           = Bits(INPUT,4)
    val c0_ddr3_s_axi_arvalid         = Bool(INPUT)
    val c0_ddr3_s_axi_arready         = Bool(OUTPUT)
    //slave interface read data ports
    val c0_ddr3_s_axi_rready          = Bool(INPUT)
    val c0_ddr3_s_axi_rid             = Bits(OUTPUT,4)
    val c0_ddr3_s_axi_rdata           = Bits(OUTPUT,64)
    val c0_ddr3_s_axi_rresp           = Bits(OUTPUT,2)
    val c0_ddr3_s_axi_rlast           = Bool(OUTPUT)
    val c0_ddr3_s_axi_rvalid          = Bool(OUTPUT)
  }

  ElaborationArtefacts.add(
    "xcvu440mig.vivado.tcl",
    """ 
      create_ip -vendor xilinx.com -library ip -version 1.4 -name ddr3 -module_name xcvu440mig -dir $ipdir -force
      set_property -dict [list \
        CONFIG.AL_SEL                               {0} \
        CONFIG.C0.ControllerType                    {DDR3_SDRAM} \
        CONFIG.C0.DDR3_AUTO_AP_COL_A3               {false} \
        CONFIG.C0.DDR3_AutoPrecharge                {false} \
        CONFIG.C0.DDR3_AxiAddressWidth              {32} \
        CONFIG.C0.DDR3_AxiArbitrationScheme         {RD_PRI_REG} \
        CONFIG.C0.DDR3_AxiDataWidth                 {64} \
        CONFIG.C0.DDR3_AxiIDWidth                   {4} \
        CONFIG.C0.DDR3_AxiNarrowBurst               {false} \
        CONFIG.C0.DDR3_AxiSelection                 {true} \
        CONFIG.C0.DDR3_BurstLength                  {8} \
        CONFIG.C0.DDR3_BurstType                    {Sequential} \
        CONFIG.C0.DDR3_CLKFBOUT_MULT                {8} \
        CONFIG.C0.DDR3_CLKOUT0_DIVIDE               {5} \
        CONFIG.C0.DDR3_Capacity                     {512} \
        CONFIG.C0.DDR3_CasLatency                   {11} \
        CONFIG.C0.DDR3_CasWriteLatency              {8} \
        CONFIG.C0.DDR3_ChipSelect                   {true} \
        CONFIG.C0.DDR3_CustomParts                  {no_file_loaded} \
        CONFIG.C0.DDR3_DIVCLK_DIVIDE                {2} \
        CONFIG.C0.DDR3_DataMask                     {true} \
        CONFIG.C0.DDR3_DataWidth                    {64} \
        CONFIG.C0.DDR3_Ecc                          {false} \
        CONFIG.C0.DDR3_MCS_ECC                      {false} \
        CONFIG.C0.DDR3_Mem_Add_Map                  {BANK_ROW_COLUMN} \
        CONFIG.C0.DDR3_MemoryName                   {MainMemory} \
        CONFIG.C0.DDR3_MemoryPart                   {MT8KTF51264HZ-1G6} \
        CONFIG.C0.DDR3_MemoryType                   {SODIMMs} \
        CONFIG.C0.DDR3_MemoryVoltage                {1.5V} \
        CONFIG.C0.DDR3_OnDieTermination             {RZQ/6} \
        CONFIG.C0.DDR3_Ordering                     {Normal} \
        CONFIG.C0.DDR3_OutputDriverImpedenceControl {RZQ/7} \
        CONFIG.C0.DDR3_PhyClockRatio                {4:1} \
        CONFIG.C0.DDR3_SAVE_RESTORE                 {false} \
        CONFIG.C0.DDR3_SELF_REFRESH                 {false} \
        CONFIG.C0.DDR3_Slot                         {Single} \
        CONFIG.C0.DDR3_Specify_MandD                {true} \
        CONFIG.C0.DDR3_TimePeriod                   {1250} \
        CONFIG.C0.DDR3_UserRefresh_ZQCS             {false} \
        CONFIG.C0.DDR3_isCKEShared                  {false} \
        CONFIG.C0.DDR3_isCustom                     {false} \
        CONFIG.C0_CLOCK_BOARD_INTERFACE             {Custom} \
        CONFIG.C0_DDR3_BOARD_INTERFACE              {Custom} \
        CONFIG.DCI_Cascade                          {true} \
        CONFIG.DIFF_TERM_SYSCLK                     {false} \
        CONFIG.Debug_Signal                         {Disable} \
        CONFIG.Default_Bank_Selections              {false} \
        CONFIG.Enable_SysPorts                      {true} \
        CONFIG.IOPowerReduction                     {OFF} \
        CONFIG.IO_Power_Reduction                   {false} \
        CONFIG.IS_FROM_PHY                          {1} \
        CONFIG.MCS_DBG_EN                           {false} \
        CONFIG.No_Controller                        {1} \
        CONFIG.PARTIAL_RECONFIG_FLOW_MIG            {false} \
        CONFIG.Phy_Only                             {Complete_Memory_Controller} \
        CONFIG.RECONFIG_XSDB_SAVE_RESTORE           {false} \
        CONFIG.RESET_BOARD_INTERFACE                {Custom} \
        CONFIG.Reference_Clock                      {Differential} \
        CONFIG.System_Clock                         {No_Buffer} \
        CONFIG.TIMING_OP1                           {false} \
        CONFIG.TIMING_OP2                           {false} \
      ] [get_ips xcvu440mig]"""
  )
   
}
//scalastyle:on

      //   CONFIG.C0.DDR3_TimePeriod {1250}                                       \
      //   CONFIG.C0.DDR3_InputClockPeriod {5000}                                 \
      //   CONFIG.C0.DDR3_MemoryType {SODIMMs}                                    \
      //   CONFIG.C0.DDR3_MemoryPart {MT8KTF51264HZ-1G6}                          \
      //   CONFIG.C0.DDR3_Mem_Add_Map {BANK_ROW_COLUMN}                           \
      //   CONFIG.C0.DDR3_AxiSelection {true}                                     \
      //   CONFIG.C0.DDR3_AxiDataWidth {64}                                       \
      //   CONFIG.C0.DDR3_isCustom {false}                                        \
      //   CONFIG.Simulation_Mode {Unisim}                                        \
      //   CONFIG.Internal_Vref {false}                                           \
      //   CONFIG.C0.DDR3_DataWidth {64}                                          \
      //   CONFIG.C0.DDR3_DataMask {true}                                        \
      //   CONFIG.C0.DDR3_Ecc {false}                                              \
      //   CONFIG.C0.DDR3_CasLatency {11}                                         \
      //   CONFIG.C0.DDR3_CasWriteLatency {8}                                     \
      //   CONFIG.C0.DDR3_AxiAddressWidth {31}                                    \
      //   CONFIG.C0.DDR3_AxiIDWidth {4}                                          \


      // CONFIG.AL_SEL                               {0} \
      // CONFIG.C0.ControllerType                    {DDR3_SDRAM} \
      // CONFIG.C0.DDR3_AUTO_AP_COL_A3               {false} \
      // CONFIG.C0.DDR3_AutoPrecharge                {false} \
      // CONFIG.C0.DDR3_AxiAddressWidth              {31} \
      // CONFIG.C0.DDR3_AxiArbitrationScheme         {RD_PRI_REG} \
      // CONFIG.C0.DDR3_AxiDataWidth                 {64} \
      // CONFIG.C0.DDR3_AxiIDWidth                   {4} \
      // CONFIG.C0.DDR3_AxiNarrowBurst               {false} \
      // CONFIG.C0.DDR3_AxiSelection                 {true} \
      // CONFIG.C0.DDR3_BurstLength                  {8} \
      // CONFIG.C0.DDR3_BurstType                    {Sequential} \
      // CONFIG.C0.DDR3_CLKFBOUT_MULT                {8} \
      // CONFIG.C0.DDR3_CLKOUT0_DIVIDE               {5} \
      // CONFIG.C0.DDR3_Capacity                     {512} \
      // CONFIG.C0.DDR3_CasLatency                   {11} \
      // CONFIG.C0.DDR3_CasWriteLatency              {8} \
      // CONFIG.C0.DDR3_ChipSelect                   {true} \
      // CONFIG.C0.DDR3_CustomParts                  {no_file_loaded} \
      // CONFIG.C0.DDR3_DIVCLK_DIVIDE                {2} \
      // CONFIG.C0.DDR3_DataMask                     {DM_NO_DBI} \
      // CONFIG.C0.DDR3_DataWidth                    {64} \
      // CONFIG.C0.DDR3_Ecc                          {false} \
      // CONFIG.C0.DDR3_MCS_ECC                      {false} \
      // CONFIG.C0.DDR3_Mem_Add_Map                  {BANK_ROW_COLUMN} \
      // CONFIG.C0.DDR3_MemoryName                   {MainMemory} \
      // CONFIG.C0.DDR3_MemoryPart                   {MT8KTF51264HZ-1G6} \
      // CONFIG.C0.DDR3_MemoryType                   {SODIMMs} \
      // CONFIG.C0.DDR3_MemoryVoltage                {1.5V} \
      // CONFIG.C0.DDR3_OnDieTermination             {RZQ/6} \
      // CONFIG.C0.DDR3_Ordering                     {Normal} \
      // CONFIG.C0.DDR3_OutputDriverImpedenceControl {RZQ/7} \
      // CONFIG.C0.DDR3_PhyClockRatio                {4:1} \
      // CONFIG.C0.DDR3_SAVE_RESTORE                 {false} \
      // CONFIG.C0.DDR3_SELF_REFRESH                 {false} \
      // CONFIG.C0.DDR3_Slot                         {Single} \
      // CONFIG.C0.DDR3_Specify_MandD                {true} \
      // CONFIG.C0.DDR3_TimePeriod                   {1250} \
      // CONFIG.C0.DDR3_UserRefresh_ZQCS             {false} \
      // CONFIG.C0.DDR3_isCKEShared                  {false} \
      // CONFIG.C0.DDR3_isCustom                     {false} \
      // CONFIG.C0_CLOCK_BOARD_INTERFACE             {Custom} \
      // CONFIG.C0_DDR3_BOARD_INTERFACE              {Custom} \
      // CONFIG.DCI_Cascade                          {true} \
      // CONFIG.DIFF_TERM_SYSCLK                     {false} \
      // CONFIG.Debug_Signal                         {Disable} \
      // CONFIG.Default_Bank_Selections              {false} \
      // CONFIG.Enable_SysPorts                      {true} \
      // CONFIG.IOPowerReduction                     {OFF} \
      // CONFIG.IO_Power_Reduction                   {false} \
      // CONFIG.IS_FROM_PHY                          {1} \
      // CONFIG.MCS_DBG_EN                           {false} \
      // CONFIG.No_Controller                        {1} \
      // CONFIG.PARTIAL_RECONFIG_FLOW_MIG            {false} \
      // CONFIG.Phy_Only                             {Complete_Memory_Controller} \
      // CONFIG.RECONFIG_XSDB_SAVE_RESTORE           {false} \
      // CONFIG.RESET_BOARD_INTERFACE                {Custom} \
      // CONFIG.Reference_Clock                      {Differential} \
      // CONFIG.System_Clock                         {No_Buffer} \
      // CONFIG.TIMING_OP1                           {false} \
      // CONFIG.TIMING_OP2                           {false} \
