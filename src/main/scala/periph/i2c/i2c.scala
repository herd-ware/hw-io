/*
 * File: i2c.scala                                                             *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:08:46 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.periph.i2c

import chisel3._
import chisel3.util._

import herd.common.gen._
import herd.common.tools.{Counter}


class I2cCtrl (p: I2cParams) extends Module {
  import herd.io.periph.i2c.I2cFSM._

  val io = IO(new Bundle {
    val i_config = Input(new I2cConfigBus())
    val o_idle = Output(Bool())
    val o_err_aa = Output(Bool())
    val o_err_da = Output(Bool())

    val b_req = Flipped(new GenRVIO(p, new I2cCtrlBus(), UInt(8.W)))
    val b_read = new GenRVIO(p, UInt(0.W), UInt(8.W))

    val b_i2c = new I2cIO()
  })

  val r_fsm = RegInit(s0IDLE)
  val r_config = Reg(new I2cConfigBus())
  val r_ctrl = Reg(new I2cCtrlBus())
  val r_err_aa = RegInit(0.B)
  val r_err_da = RegInit(0.B)
  
  val r_scl_eno = RegInit(0.B)
  val r_scl_out = RegInit(1.B)
  val r_sda_eno = RegInit(0.B)
  val r_sda_out = RegInit(1.B)

  val r_addr = Reg(UInt(7.W))
  val r_data = Reg(UInt(8.W))
  val r_in = Reg(Bool())

  val w_same = Wire(Bool())

  // ******************************
  //            COUNTERS
  // ******************************
  val m_ccnt = Module(new Counter(32))
  val m_bcnt = Module(new Counter(3))

  m_ccnt.io.i_limit := r_config.ncycle
  m_ccnt.io.i_init := (r_fsm === s0IDLE)
  m_ccnt.io.i_en := (r_fsm =/= s0IDLE)

  m_bcnt.io.i_limit := Mux((r_fsm === s2ADDR), 7.U, 0.U)
  m_bcnt.io.i_init := true.B
  m_bcnt.io.i_en := false.B

  // ******************************
  //             SCLK
  // ******************************
  val w_rise = (m_ccnt.io.o_val === ((r_config.ncycle >> 2.U) - 1.U))
  val w_half = (m_ccnt.io.o_val === ((r_config.ncycle >> 1.U) - 1.U))
  val w_fall = (m_ccnt.io.o_val === ((r_config.ncycle >> 1.U) + (r_config.ncycle >> 2.U) - 1.U))
  val w_full = (m_ccnt.io.o_val === (r_config.ncycle - 1.U))

  when ((r_fsm =/= s0IDLE) & (r_fsm =/= s1START) & (r_fsm =/= s8STOP) & (r_fsm =/= s9DELAY)) {
    when (w_rise) {
      r_scl_out := 1.B
    }
    when (w_fall) {
      r_scl_out := 0.B
    }
  }.elsewhen (r_fsm === s1START) {
    when (w_half) {
      r_scl_out := 0.B
    }    
  }.elsewhen (r_fsm === s8STOP) {
    when (w_half) {
      r_scl_out := 1.B
    }   
  }.otherwise {
    r_scl_out := 1.B
  }

  // ******************************
  //              FSM
  // ******************************
  w_same := io.b_req.valid & io.b_req.ctrl.get.mb & (r_ctrl.rw === io.b_req.ctrl.get.rw) & (r_config.addr === io.i_config.addr)

  switch (r_fsm) {
    // ------------------------------
    //             IDLE
    // ------------------------------
    is (s0IDLE) {
      when (io.b_req.valid) {
        r_fsm := s1START
        r_config := io.i_config
        r_addr := io.i_config.addr
        r_ctrl := io.b_req.ctrl.get    

        r_err_aa := 0.B
        r_err_da := 0.B
      }
    }

    // ------------------------------
    //            START
    // ------------------------------
    is (s1START) {
      when (w_full) {
        r_fsm := s2ADDR
      }
    }

    // ------------------------------
    //            ADDRESS
    // ------------------------------
    is (s2ADDR) {
      m_bcnt.io.i_init := false.B
      m_bcnt.io.i_en := false.B
      when (w_full) {
        when (m_bcnt.io.o_flag) {
          r_fsm := s3RW
          m_bcnt.io.i_init := true.B
        }.otherwise {
          m_bcnt.io.i_en := true.B
        }
      }
    }

    // ------------------------------
    //              RW
    // ------------------------------
    is (s3RW) {
      when (w_full) {
        r_fsm := s4AACK
      }
    }

    // ------------------------------
    //          ADDRESS ACK
    // ------------------------------
    is (s4AACK) {
      when (w_full) {
        when (~r_in) {
          r_fsm := s5DATA
        }.otherwise {
          r_fsm := s8STOP
          r_err_aa := 1.B
        }        
      }
    }

    // ------------------------------
    //             DATA
    // ------------------------------
    is (s5DATA) {
      m_bcnt.io.i_init := false.B
      m_bcnt.io.i_en := false.B
      when (w_full) {
        when (m_bcnt.io.o_flag) {
          r_fsm := s6DACK
          m_bcnt.io.i_init := true.B
        }.otherwise {
          m_bcnt.io.i_en := true.B
        }
      }
    }

    // ------------------------------
    //           DATA ACK
    // ------------------------------
    is (s6DACK) {
      when (m_ccnt.io.o_val === (r_config.ncycle - 2.U)) {
        when (~r_ctrl.rw | ~r_in) {
          r_fsm := s7END
        }     
      }

      when (w_full) {
        r_fsm := s8STOP
        r_err_da := 1.B
      }
    }

    // ------------------------------
    //             END
    // ------------------------------
    is (s7END) {
      when (w_same & (r_ctrl.rw | (r_sda_out === BIT.ACK.B))) {
        r_fsm := s5DATA    
        r_addr := io.i_config.addr
      }.otherwise {
        r_fsm := s8STOP
      }
    }

    // ------------------------------
    //             STOP
    // ------------------------------
    is (s8STOP) {
      when (w_full) {
        r_fsm := s9DELAY
      }
    }

    // ------------------------------
    //             DELAY
    // ------------------------------
    is (s9DELAY) {
      when (w_full) {
        r_fsm := s0IDLE
      }
    }
  }

  // ******************************
  //              DATA
  // ******************************
  when (w_half) {
    r_in := io.b_i2c.sda.in
  }

  switch (r_fsm) {
    // ------------------------------
    //             IDLE
    // ------------------------------
    is (s0IDLE) {
      when (io.b_req.valid) {
        r_data := io.b_req.data.get
        r_scl_eno := true.B    
        r_sda_eno := true.B    
        r_sda_out := BIT.START.B    
      }
    }

    // ------------------------------
    //            START
    // ------------------------------
    is (s1START) {
      when (w_full) {
        r_addr := (r_addr << 1.U)
        r_sda_out := r_addr(6) 
      }
    }

    // ------------------------------
    //            ADDRESS
    // ------------------------------
    is (s2ADDR) {
      when (w_full) {
        when (m_bcnt.io.o_flag) {
          r_sda_out := ~r_ctrl.rw
        }.otherwise {
          r_addr := (r_addr << 1.U)
          r_sda_out := r_addr(6) 
        }
      }
    }

    // ------------------------------
    //              RW
    // ------------------------------
    is (s3RW) {
      when (w_full) {
        r_sda_eno := false.B    
        r_sda_out := 1.B
      }
    }

    // ------------------------------
    //          ADDRESS ACK
    // ------------------------------
    is (s4AACK) {
      when (w_full) {
        when (~r_in) {
          r_data := (r_data << 1.U)  
          r_sda_eno := r_ctrl.rw    
          r_sda_out := r_data(7) 
        }.otherwise {
          r_scl_eno := true.B    
          r_sda_eno := true.B    
          r_sda_out := BIT.STOP.B
        }        
      }
    }

    // ------------------------------
    //             DATA
    // ------------------------------
    is (s5DATA) {
      when (w_half & ~r_ctrl.rw) {
        r_data := Cat(r_data(6, 0), io.b_i2c.sda.in)
      }

      when (w_full) {
        when (m_bcnt.io.o_flag) {
          when (r_ctrl.rw) {
            r_sda_eno := false.B
            r_sda_out := true.B
          }.otherwise {
            r_sda_eno := true.B
            when (w_same) {
              r_sda_out := BIT.ACK.B
            }.otherwise {
              r_sda_out := BIT.NACK.B
            }            
          }
        }.elsewhen(r_ctrl.rw) {
          r_data := (r_data << 1.U)
          r_sda_out := r_data(7) 
        }
      }
    }

    // ------------------------------
    //           DATA ACK
    // ------------------------------
    is (s6DACK) {
      when (w_full) {
        r_scl_eno := true.B    
        r_sda_eno := true.B    
        r_sda_out := BIT.STOP.B
      }
    }

    // ------------------------------
    //             END
    // ------------------------------
    is (s7END) {
      when (w_same & (r_ctrl.rw | (r_sda_out === BIT.ACK.B))) {
        r_data := (io.b_req.data.get << 1.U)
        r_sda_eno := r_ctrl.rw    
        r_sda_out := io.b_req.data.get(7) 
      }.otherwise {
        r_scl_eno := true.B    
        r_sda_eno := true.B    
        r_sda_out := BIT.STOP.B
      }
    }

    // ------------------------------
    //             STOP
    // ------------------------------
    is (s8STOP) {
      when (w_full) {
        r_scl_eno := false.B    
        r_sda_eno := false.B  
        r_sda_out := BIT.IDLE.B
      }
    }
  }

  // ******************************
  //             I/Os
  // ******************************
  io.o_idle := false.B
  io.o_err_aa := r_err_aa
  io.o_err_da := r_err_da
  io.b_req.ready := false.B
  io.b_read.valid := false.B
  io.b_read.data.get := r_data

  io.b_i2c.scl.eno := r_scl_eno
  io.b_i2c.scl.out := r_scl_out
  io.b_i2c.sda.eno := r_sda_eno
  io.b_i2c.sda.out := r_sda_out

  switch (r_fsm){
    // ------------------------------
    //             IDLE
    // ------------------------------
    is (s0IDLE) {
      io.b_req.ready := true.B
    }

    // ------------------------------
    //             END
    // ------------------------------
    is (s7END) {
      io.b_req.ready := io.b_req.ctrl.get.mb & (r_ctrl.rw === io.b_req.ctrl.get.rw) & (r_config.addr === io.i_config.addr)
      io.b_read.valid := ~r_ctrl.rw
    }
  }

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {

  }
}

class I2c (p: I2cParams) extends Module {  
  val io = IO(new Bundle {
    val o_irq_req = Output(Bool())
    val o_irq_read = Output(Bool())    

    val b_regmem = if (p.useRegMem) Some(new I2cRegMemIO(p, p.nDataByte)) else None    

    val o_status = if (!p.useRegMem) Some(Output(new I2cStatusBus())) else None
    val i_config = if (!p.useRegMem) Some(Input(new I2cConfigBus())) else None
    val b_port = if (!p.useRegMem) Some(new I2cPortIO(p, p.nDataByte)) else None

    val b_i2c = new I2cIO()
  })

  val m_creq = Module(new GenFifo(p,new I2cCtrlBus(), UInt(0.W), 4, p.nBufferDepth, p.nDataByte, 1))
  val m_dreq = Module(new GenFifo(p, UInt(0.W), UInt(8.W), 4, p.nBufferDepth, p.nDataByte, 1))
  val m_i2c = Module(new I2cCtrl(p))
  val m_read = Module(new GenFifo(p, UInt(0.W), UInt(8.W), 4, p.nBufferDepth, 1, p.nDataByte))

  val r_status = Reg(new I2cStatusBus())
  val r_config = Reg(new I2cConfigBus())

  val w_status = Wire(new I2cStatusBus())
  val w_config = Wire(new I2cConfigBus())
  val w_idle = Wire(Bool())  

  // ******************************
  //            STATUS
  // ******************************
  w_status := r_status

  w_status.err_aa := m_i2c.io.o_err_aa
  w_status.err_da := m_i2c.io.o_err_da
  
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

  w_idle := ~m_creq.io.b_out(0).valid & ~m_dreq.io.b_out(0).valid & m_i2c.io.o_idle

  // ------------------------------
  //             READ
  // ------------------------------ 
  if (p.useRegMem) {
    io.b_regmem.get.status := Cat(  0.U(8.W),
                                    0.U(4.W), w_status.av.asUInt,
                                    0.U(4.W), w_status.full.asUInt,
                                    w_status.err_da, w_status.err_aa, 0.U(5.W), w_status.idle)
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
                                    0.U(8.W), 
                                    0.U(7.W), r_config.en)
    io.b_regmem.get.ncycle := r_config.ncycle
    io.b_regmem.get.addr := r_config.addr
  }

  // ------------------------------
  //             WRITE
  // ------------------------------
  if (p.useRegMem) {
    when (io.b_regmem.get.wen(1)) {
      r_config.en := io.b_regmem.get.wdata(0)
      r_config.irq := io.b_regmem.get.wdata(24 + IRQ.NBIT - 1, 24)
    }
  
    when (io.b_regmem.get.wen(2)) {
      r_config.ncycle := io.b_regmem.get.wdata
    }
  
    when (io.b_regmem.get.wen(3)) {
      r_config.addr := io.b_regmem.get.wdata
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
      m_creq.io.b_in(b).ctrl.get.rw := io.b_regmem.get.creq(b).ctrl.get(0)
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
  val w_is_write = m_creq.io.b_out(0).ctrl.get.rw
  
  m_i2c.io.i_config := w_config

  m_creq.io.b_out(0).ready := m_i2c.io.b_req.ready & (~w_is_write | m_dreq.io.b_out(0).valid)
  m_dreq.io.b_out(0).ready := m_i2c.io.b_req.ready & m_creq.io.b_out(0).valid & w_is_write
  m_i2c.io.b_req.valid := m_creq.io.b_out(0).valid & (~w_is_write | m_dreq.io.b_out(0).valid)
  m_i2c.io.b_req.ctrl.get := m_creq.io.b_out(0).ctrl.get
  m_i2c.io.b_req.data.get := m_dreq.io.b_out(0).data.get

  m_i2c.io.b_i2c <> io.b_i2c

  // ------------------------------
  //             READ
  // ------------------------------  
  m_read.io.i_flush := false.B

  m_read.io.b_in(0) <> m_i2c.io.b_read
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

object I2cCtrl extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new I2cCtrl(I2cConfigBase), args)
}

object I2c extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new I2c(I2cConfigBase), args)
}

