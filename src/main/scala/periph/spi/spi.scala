/*
 * File: spi.scala                                                             *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:09:32 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.periph.spi

import chisel3._
import chisel3.util._

import herd.common.gen._
import herd.common.tools.{Counter}


class SpiCtrl (p: SpiParams) extends Module {
  import herd.io.periph.spi.SpiFSM._

  val io = IO(new Bundle {
    val i_config = Input(new SpiConfigBus(p))
    val o_idle = Output(Bool())

    val b_req = Flipped(new GenRVIO(p, new SpiCtrlBus(), UInt(8.W)))
    val b_read = new GenRVIO(p, UInt(0.W), UInt(8.W))

    val b_spi = new SpiIO(p.nSlave)
  })

  val r_fsm = RegInit(s0IDLE)
  val r_config = Reg(new SpiConfigBus(p))
  val r_ctrl = Reg(new SpiCtrlBus())
  
  val r_csn = RegInit(VecInit(Seq.fill(p.nSlave)(1.B)))
  val r_sclk = RegInit(0.B)
  val r_rdata = Reg(UInt(8.W))
  val r_wdata = Reg(UInt(8.W))

  // ******************************
  //            COUNTERS
  // ******************************
  val m_ccnt = Module(new Counter(32))
  val m_bcnt = Module(new Counter(3))

  m_ccnt.io.i_limit := r_config.ncycle
  m_ccnt.io.i_init := (r_fsm === s0IDLE) | (r_fsm === s3END)
  m_ccnt.io.i_en := (r_fsm =/= s0IDLE) & (r_fsm =/= s3END)

  m_bcnt.io.i_limit := 0.U
  m_bcnt.io.i_init := true.B
  m_bcnt.io.i_en := false.B

  // ******************************
  //             SCLK
  // ******************************
  val w_half = (m_ccnt.io.o_val === ((r_config.ncycle >> 1.U) - 1.U))
  val w_full = (m_ccnt.io.o_val === (r_config.ncycle - 1.U))

  when ((r_fsm =/= s0IDLE) & (r_fsm =/= s1SYNC)) {
    when (w_half | w_full) {
      r_sclk := ~r_sclk
    }
  } .otherwise {
    r_sclk := 0.B
  }

  // ******************************
  //              FSM
  // ******************************
  switch (r_fsm){
    // ------------------------------
    //             IDLE
    // ------------------------------
    is (s0IDLE) {
      when (io.b_req.valid) {
        r_fsm := s1SYNC
        r_config := io.i_config
        r_ctrl := io.b_req.ctrl.get

        r_csn(io.i_config.slave) := false.B        
      }
    }

    // ------------------------------
    //          SYNCHRONIZE
    // ------------------------------
    is (s1SYNC) {
      when (m_ccnt.io.o_flag){
        r_fsm := s2DATA
      }
    }

    // ------------------------------
    //             DATA
    // ------------------------------
    is (s2DATA) {
      m_bcnt.io.i_init := false.B
      m_bcnt.io.i_en := false.B

      when (m_bcnt.io.o_val === 7.U) {
        when (m_ccnt.io.o_val === (r_config.ncycle - 2.U)) {
          m_bcnt.io.i_init := true.B
          r_fsm := s3END
        }
      }.otherwise {
        when (m_ccnt.io.o_flag) {
          m_bcnt.io.i_en := true.B
        }
      }
    }

    // ------------------------------
    //             END
    // ------------------------------
    is (s3END) {
      when (io.b_req.valid & (io.i_config.slave === r_config.slave) & io.b_req.ctrl.get.mb) {
        r_fsm := s2DATA
        r_ctrl := io.b_req.ctrl.get
      }.elsewhen(io.b_req.valid & (io.i_config.slave =/= r_config.slave)) {
        r_fsm := s1SYNC
        r_config := io.i_config
        r_ctrl := io.b_req.ctrl.get

        r_csn(r_config.slave) := true.B
        r_csn(io.i_config.slave) := false.B
      }.otherwise {
        r_fsm := s4DELAY
        
        r_csn(r_config.slave) := true.B
      }
    }

    // ------------------------------
    //            DELAY
    // ------------------------------
    is (s4DELAY) {
      when (io.b_req.valid & (io.i_config.slave === r_config.slave)) {
        r_fsm := s1SYNC
        r_config := io.i_config
        r_ctrl := io.b_req.ctrl.get

        r_csn(r_config.slave) := true.B
        r_csn(io.i_config.slave) := false.B
      }.otherwise {
        when (m_ccnt.io.o_flag) {
          r_fsm := s0IDLE
        }
      }
    }
  }

  // ******************************
  //              DATA
  // ******************************
  switch (r_fsm){
    // ------------------------------
    //             IDLE
    // ------------------------------
    is (s0IDLE) {
      when (io.b_req.valid) {
        when (io.i_config.big) {
          r_wdata := Reverse(io.b_req.data.get)
        }.otherwise {
          r_wdata := io.b_req.data.get
        }
        
      }
    }

    // ------------------------------
    //             DATA
    // ------------------------------
    is (s2DATA) {
      when (w_half) {
        switch (r_config.mode) {
          is (MODE.BASE) {
            when (r_config.big) {
              r_rdata := Cat(r_rdata(6, 0), io.b_spi.data.in(1))
            }.otherwise {
              r_rdata := Cat(io.b_spi.data.in(0), r_rdata(7, 1))
            }            
          }
          is (MODE.DUAL) {
            when (r_config.big) {
              r_rdata := Cat(r_rdata(5, 0), io.b_spi.data.in(1, 0))
            }.otherwise {
              r_rdata := Cat(io.b_spi.data.in(1, 0), r_rdata(7, 2))
            }            
          }
          is (MODE.QUAD) {
            when (r_config.big) {
              r_rdata := Cat(r_rdata(3, 0), io.b_spi.data.in(3, 0))
            }.otherwise {
              r_rdata := Cat(io.b_spi.data.in(3, 0), r_rdata(7, 4))
            }            
          }
        }
      }

      when (m_ccnt.io.o_flag) {
        switch (r_config.mode) {
          is (MODE.BASE) {  r_wdata := (r_wdata >> 1.U)}
          is (MODE.DUAL) {  r_wdata := (r_wdata >> 2.U)}
          is (MODE.QUAD) {  r_wdata := (r_wdata >> 4.U)}
        }
      }
    }

    // ------------------------------
    //             END
    // ------------------------------
    is (s3END) {
      when (io.b_req.valid & (io.i_config.slave === r_config.slave) & io.b_req.ctrl.get.mb) {
        when (r_config.big) {
          r_wdata := Reverse(io.b_req.data.get)
        }.otherwise {
          r_wdata := io.b_req.data.get
        }
      }.elsewhen(io.b_req.valid & (io.i_config.slave =/= r_config.slave)) {
        when (io.i_config.big) {
          r_wdata := Reverse(io.b_req.data.get)
        }.otherwise {
          r_wdata := io.b_req.data.get
        }
      }
    }

    // ------------------------------
    //            DELAY
    // ------------------------------
    is (s4DELAY) {
      when (io.b_req.valid & (io.i_config.slave === r_config.slave)) {
        when (io.i_config.big) {
          r_wdata := Reverse(io.b_req.data.get)
        }.otherwise {
          r_wdata := io.b_req.data.get
        }
      }
    }
  }

  // ******************************
  //             I/Os
  // ******************************
  io.o_idle := false.B
  io.b_req.ready := false.B
  io.b_read.valid := false.B
  io.b_read.data.get := r_rdata

  switch (r_fsm){
    // ------------------------------
    //             IDLE
    // ------------------------------
    is (s0IDLE) {
      io.o_idle := true.B
      io.b_req.ready := true.B
    }

    // ------------------------------
    //             END
    // ------------------------------
    is (s3END) {
      io.b_req.ready := (io.i_config.slave === r_config.slave) & io.b_req.ctrl.get.mb | (io.i_config.slave =/= r_config.slave)
      io.b_read.valid := (r_ctrl.cmd === CMD.R) | (r_ctrl.cmd === CMD.RW)
    }

    // ------------------------------
    //            DELAY
    // ------------------------------
    is (s4DELAY) {
      io.b_req.ready := (io.i_config.slave =/= r_config.slave)
    }
  }

  io.b_spi.csn := r_csn
  io.b_spi.sclk := r_sclk
  io.b_spi.data.eno := 0.U
  io.b_spi.data.out := r_wdata
  switch (r_config.mode) {
    is (MODE.BASE) {  io.b_spi.data.eno := 1.U}
    is (MODE.DUAL) {  io.b_spi.data.eno := Mux(((r_ctrl.cmd === CMD.W) | (r_ctrl.cmd === CMD.RW)), 3.U,   0.U)}
    is (MODE.QUAD) {  io.b_spi.data.eno := Mux(((r_ctrl.cmd === CMD.W) | (r_ctrl.cmd === CMD.RW)), 15.U,  0.U)}
  }

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {

  }
}

class Spi (p: SpiParams) extends Module {
  require((p.nSlave <= 8), "Only 8 SPI slaves can be connected to Spi.")
  
  val io = IO(new Bundle {
    val o_irq_req = Output(Bool())
    val o_irq_read = Output(Bool())    

    val b_regmem = if (p.useRegMem) Some(new SpiRegMemIO(p, p.nDataByte)) else None    

    val o_status = if (!p.useRegMem) Some(Output(new SpiStatusBus())) else None
    val i_config = if (!p.useRegMem) Some(Input(new SpiConfigBus(p))) else None
    val b_port = if (!p.useRegMem) Some(new SpiPortIO(p, p.nDataByte)) else None

    val b_spi = new SpiIO(p.nSlave)
  })

  val m_creq = Module(new GenFifo(p,new SpiCtrlBus(), UInt(0.W), 4, p.nBufferDepth, p.nDataByte, 1))
  val m_dreq = Module(new GenFifo(p, UInt(0.W), UInt(8.W), 4, p.nBufferDepth, p.nDataByte, 1))
  val m_spi = Module(new SpiCtrl(p))
  val m_read = Module(new GenFifo(p, UInt(0.W), UInt(8.W), 4, p.nBufferDepth, 1, p.nDataByte))

  val r_status = Reg(new SpiStatusBus())
  val r_config = Reg(new SpiConfigBus(p))

  val w_status = Wire(new SpiStatusBus())
  val w_config = Wire(new SpiConfigBus(p))
  val w_idle = Wire(Bool())  

  // ******************************
  //            STATUS
  // ******************************
  w_status := r_status
  
  w_status.full(0) := ~m_creq.io.b_in(0).ready | ~m_dreq.io.b_in(0).ready
  w_status.full(1) := ~m_creq.io.b_in(1).ready | ~m_dreq.io.b_in(1).ready
  w_status.full(2) := ~m_creq.io.b_in(3).ready | ~m_dreq.io.b_in(3).ready
  if (p.nDataByte > 4) {
    w_status.full(3) := ~m_creq.io.b_in(7).ready | ~m_dreq.io.b_in(7).ready
  } else {
    w_status.full(3) := true.B
  }

  w_status.av(0) := m_read.io.o_val(0).valid
  w_status.av(1) := m_read.io.o_val(1).valid
  w_status.av(2) := m_read.io.o_val(3).valid
  if (p.nDataByte >= 4) {
    w_status.av(3) := m_read.io.o_val(7).valid
  } else {
    w_status.av(3) := true.B
  }

  w_idle := ~m_creq.io.b_out(0).valid & ~m_dreq.io.b_out(0).valid & m_spi.io.o_idle

  // ------------------------------
  //             READ
  // ------------------------------ 
  if (p.useRegMem) {
    io.b_regmem.get.status := Cat(  0.U(8.W),
                                    0.U(4.W), w_status.av.asUInt,
                                    0.U(4.W), w_status.full.asUInt,
                                    0.U(7.W), w_status.idle)
  } else {
    io.o_status.get := r_status
  }

  // ------------------------------
  //             WRITE
  // ------------------------------ 
  r_status.idle := w_idle

  // ******************************
  //            CONFIG
  // ******************************
  w_config := r_config

  // ------------------------------
  //             READ
  // ------------------------------  
  if (p.useRegMem) {
    io.b_regmem.get.config := Cat(  0.U((8 - IRQ.NBIT).W), r_config.irq,
                                    0.U(8.W),
                                    0.U(5.W), (r_config.slave + 0.U(3.W)), 
                                    0.U(2.W), r_config.big, r_config.mode, r_config.cpha, r_config.cpol, r_config.en)
    io.b_regmem.get.ncycle := r_config.ncycle
  }

  // ------------------------------
  //             WRITE
  // ------------------------------
  if (p.useRegMem) {
    when (io.b_regmem.get.wen(1)) {
      r_config.en := io.b_regmem.get.wdata(0)
      r_config.cpol := io.b_regmem.get.wdata(1)
      r_config.cpha := io.b_regmem.get.wdata(0)
      r_config.mode := io.b_regmem.get.wdata(4, 3)
      r_config.big := io.b_regmem.get.wdata(5)
      r_config.slave := io.b_regmem.get.wdata(10, 8)
      r_config.irq := io.b_regmem.get.wdata(24 + IRQ.NBIT - 1, 24)
    }
  
    when (io.b_regmem.get.wen(2)) {
      r_config.ncycle := io.b_regmem.get.wdata
    }
  }

  // ******************************
  //            MASTER
  // ******************************
  // ------------------------------
  //            REQUEST
  // ------------------------------
  m_creq.io.i_flush := false.B
  m_dreq.io.i_flush := false.B

  if (p.useRegMem) {
    for (b <- 0 until p.nDataByte) {
      io.b_regmem.get.creq(b).ready := m_creq.io.b_in(b).ready
      m_creq.io.b_in(b).valid := io.b_regmem.get.creq(b).valid
      m_creq.io.b_in(b).ctrl.get.cmd := io.b_regmem.get.creq(b).ctrl.get(CMD.NBIT - 1, 0)
      m_creq.io.b_in(b).ctrl.get.mb := io.b_regmem.get.creq(b).ctrl.get(7)
    }

    m_dreq.io.b_in <> io.b_regmem.get.dreq
  } else {
    m_creq.io.b_in <> io.b_port.get.creq
    m_dreq.io.b_in <> io.b_port.get.dreq
  }

  // ------------------------------
  //          CONTROLLER
  // ------------------------------
  val w_is_write = ((m_creq.io.b_out(0).ctrl.get.cmd === CMD.W) | (m_creq.io.b_out(0).ctrl.get.cmd === CMD.RW))
  
  m_spi.io.i_config := w_config

  m_creq.io.b_out(0).ready := m_spi.io.b_req.ready & (~w_is_write | m_dreq.io.b_out(0).valid)
  m_dreq.io.b_out(0).ready := m_spi.io.b_req.ready & m_creq.io.b_out(0).valid & w_is_write
  m_spi.io.b_req.valid := m_creq.io.b_out(0).valid & (~w_is_write | m_dreq.io.b_out(0).valid)
  m_spi.io.b_req.ctrl.get := m_creq.io.b_out(0).ctrl.get
  m_spi.io.b_req.data.get := m_dreq.io.b_out(0).data.get

  m_spi.io.b_spi <> io.b_spi

  // ------------------------------
  //             READ
  // ------------------------------  
  m_read.io.i_flush := false.B

  m_read.io.b_in(0) <> m_spi.io.b_read
  if (p.useRegMem) {
    io.b_regmem.get.read <> m_read.io.b_out
  } else {
    io.b_port.get.read <> m_read.io.b_out
  }

  // ******************************
  //           INTERRUPT
  // ******************************
  io.o_irq_req := ~r_status.idle & w_idle

  io.o_irq_read := false.B
  switch (w_config.irq) {
    is (IRQ.B1)   { io.o_irq_read := m_read.io.o_val(0).valid}
    is (IRQ.B2)   { io.o_irq_read := m_read.io.o_val(1).valid}
    is (IRQ.B4)   { io.o_irq_read := m_read.io.o_val(3).valid}
    is (IRQ.B8)   {
      if (p.nDataByte >= 8) {
                    io.o_irq_read := m_read.io.o_val(7).valid
      }      
    }
  }

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    
  }
}

object SpiCtrl extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new SpiCtrl(SpiConfigBase), args)
}

object Spi extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Spi(SpiConfigBase), args)
}

