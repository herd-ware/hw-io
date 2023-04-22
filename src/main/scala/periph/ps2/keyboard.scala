/*
 * File: keyboard.scala                                                        *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-04-03 10:09:50 am                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.periph.ps2

import chisel3._
import chisel3.util._

import herd.common.gen._
import herd.common.tools.{Counter}


class Ps2KeyboardCtrl (p: Ps2Params) extends Module {
  import herd.io.periph.ps2.Ps2FSM._

  val io = IO(new Bundle {
    val o_idle = Output(Bool())
    val i_config = Input(new Ps2KeyboardConfigBus())

    val b_in = Flipped(new GenRVIO(p, UInt(0.W), UInt(8.W)))
    val b_out = new GenRVIO(p, Bool(), UInt(8.W))

    val b_ps2 = new Ps2IO()
  })

  val r_fsm = RegInit(s0IDLE)
  val r_config = Reg(new Ps2KeyboardConfigBus())
  
  val r_clk = RegInit(0.B)
  val r_clk_in = RegInit(0.B)
  val r_clk_old = RegInit(0.B)
  val r_eno = RegInit(0.B)
  val r_data = Reg(UInt(8.W))
  val r_error = Reg(Bool())
  val r_wdata = Reg(UInt(8.W))

  // ******************************
  //            COUNTERS
  // ******************************
  val m_ccnt = Module(new Counter(33))
  val m_bcnt = Module(new Counter(3))

  m_ccnt.io.i_limit := (r_config.cycle << 1.U)
  m_ccnt.io.i_init := (r_fsm === s0IDLE) | (r_fsm === s4REND)
  m_ccnt.io.i_en := (r_fsm =/= s0IDLE) & (r_fsm =/= s4REND)

  m_bcnt.io.i_limit := 0.U
  m_bcnt.io.i_init := true.B
  m_bcnt.io.i_en := false.B

  // ******************************
  //             SCLK
  // ******************************
  val w_fall = Wire(Bool())
  val w_rise = Wire(Bool())

  val w_half = (m_ccnt.io.o_val === ((r_config.cycle >> 1.U) - 1.U))
  val w_full = (m_ccnt.io.o_val === (r_config.cycle - 1.U))
  val w_double = (m_ccnt.io.o_val === ((r_config.cycle << 1.U) - 1.U))

  when (r_fsm =/= s0IDLE) {
    when (w_half | w_full) {
      r_clk := ~r_clk
    }
  } .otherwise {
    r_clk := 1.B
  }

  r_clk_in := io.b_ps2.clk.in
  r_clk_old := r_clk_in
  w_fall := r_clk_old & ~r_clk_in
  w_rise := ~r_clk_old & r_clk_in

  // ******************************
  //              FSM
  // ******************************
  switch (r_fsm) {
    // ------------------------------
    //             IDLE
    // ------------------------------
    is (s0IDLE) {
      when (io.b_in.valid) {
        r_fsm := s1SSTART   
        r_config := io.i_config
      }.elsewhen(w_fall & ~io.b_ps2.data.in) {
        r_fsm := s1RDATA
        r_config := io.i_config
      }
    }

    // ------------------------------
    //              REC
    // ------------------------------
    // ..............................
    //             DATA
    // ..............................
    is (s1RDATA) {
      m_bcnt.io.i_init := false.B
      m_bcnt.io.i_en := false.B

      when (w_fall) {
        m_ccnt.io.i_init := true.B
        when (m_bcnt.io.o_flag) {
          r_fsm := s2RPARITY
          m_bcnt.io.i_init := true.B
        }.otherwise {
          m_bcnt.io.i_en := true.B
        }
      }
    }

    // ..............................
    //            PARITY
    // ..............................
    is (s2RPARITY) {
      when (w_fall) {
        r_fsm := s3RSTOP
        m_ccnt.io.i_init := true.B
      }
    }

    // ..............................
    //             STOP
    // ..............................
    is (s3RSTOP) {
      when (w_fall) {
        r_fsm := s4REND
        m_ccnt.io.i_init := true.B
      }
    }

    // ..............................
    //             END
    // ..............................
    is (s4REND) {
      r_fsm := s0IDLE      
    }
  }

  // Reset when incomplete
  when (w_double) {
    r_fsm := s0IDLE           
  }

  // ******************************
  //             DATA
  // ******************************
  switch (r_fsm) {
    // ..............................
    //             DATA
    // ..............................
    is (s1RDATA) {
      when (w_fall) {
        r_data := Cat(io.b_ps2.data.in, r_data(7, 1))
      }
    }

    // ..............................
    //            PARITY
    // ..............................
    is (s2RPARITY) {
      when (w_fall) {
        r_error := (r_data.xorR ^ io.b_ps2.data.in)
      }
    }
  }

  // ******************************
  //             I/Os
  // ******************************
  io.o_idle := false.B 
  io.b_in.ready := false.B 
  io.b_out.valid := false.B 
  io.b_out.ctrl.get := r_error
  io.b_out.data.get := r_data

  switch (r_fsm) {
    // ------------------------------
    //             IDLE
    // ------------------------------
    is (s0IDLE) {
      io.o_idle := true.B
      io.b_in.ready := true.B
      when (io.b_in.valid) {
        r_eno := true.B  
      }.elsewhen(w_fall) {
        r_eno := false.B
      }
    }

    // ------------------------------
    //            REC END
    // ------------------------------
    is (s4REND) {
      io.b_out.valid := true.B
      when (w_fall) {
        r_eno := false.B
      }
    }
  }

  io.b_ps2.clk.eno := r_eno
  io.b_ps2.clk.out := r_clk
  io.b_ps2.data.eno := r_eno
  io.b_ps2.data.out := r_data(0)


  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {

  }
}

class Ps2Keyboard (p: Ps2Params) extends Module {  
  val io = IO(new Bundle {
    val o_irq_send = Output(Bool())
    val o_irq_rec = Output(Bool())    

    val b_regmem = if (p.useRegMem) Some(new Ps2KeyboardRegMemIO(p, p.nDataByte)) else None    

    val o_status = if (!p.useRegMem) Some(Output(new Ps2KeyboardStatusBus())) else None
    val i_config = if (!p.useRegMem) Some(Input(new Ps2KeyboardConfigBus())) else None
    val b_port = if (!p.useRegMem) Some(new Ps2PortIO(p, p.nDataByte)) else None

    val b_ps2 = new Ps2IO()
  })

  val m_send = Module(new GenFifo(p, UInt(0.W), UInt(8.W), 4, p.nBufferDepth, p.nDataByte, 1))
  val m_ps2 = Module(new Ps2KeyboardCtrl(p))
  val m_rec = Module(new GenFifo(p, Bool(), UInt(8.W), 4, p.nBufferDepth, 1, p.nDataByte))

  val r_status = Reg(new Ps2KeyboardStatusBus())
  val r_config = Reg(new Ps2KeyboardConfigBus())

  val w_status = Wire(new Ps2KeyboardStatusBus())
  val w_config = Wire(new Ps2KeyboardConfigBus())
  val w_idle = Wire(Bool())  

  // ******************************
  //            STATUS
  // ******************************
  w_status := r_status
  
  w_status.full(0) := ~m_send.io.b_in(0).ready
  w_status.full(1) := ~m_send.io.b_in(1).ready
  w_status.full(2) := ~m_send.io.b_in(3).ready
  if (p.nDataByte > 4) {
    w_status.full(3) := ~m_send.io.b_in(7).ready
  } else {
    w_status.full(3) := true.B
  }

  w_status.av(0) := m_rec.io.o_val(0).valid
  w_status.av(1) := m_rec.io.o_val(1).valid
  w_status.av(2) := m_rec.io.o_val(3).valid
  if (p.nDataByte >= 4) {
    w_status.av(3) := m_rec.io.o_val(7).valid
  } else {
    w_status.av(3) := false.B
  }

  w_idle := ~m_send.io.b_out(0).valid & m_ps2.io.o_idle

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
                                    0.U(8.W),
                                    0.U(7.W), r_config.en)
    io.b_regmem.get.cycle := r_config.cycle
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
      r_config.cycle := io.b_regmem.get.wdata
    }
  }

  // ******************************
  //            KEYBOARD
  // ******************************
  // ------------------------------
  //             SEND
  // ------------------------------
  m_send.io.i_flush := false.B

  if (p.useRegMem) {
    m_send.io.b_in <> io.b_regmem.get.send    
  } else {
    m_send.io.b_in <> io.b_port.get.send
  }

  // ------------------------------
  //          CONTROLLER
  // ------------------------------  
  m_ps2.io.i_config := w_config

  m_ps2.io.b_in <> m_send.io.b_out(0)

  m_ps2.io.b_ps2 <> io.b_ps2

  // ------------------------------
  //             REC
  // ------------------------------  
  m_rec.io.i_flush := false.B

  m_rec.io.b_in(0) <> m_ps2.io.b_out
  if (p.useRegMem) {
    io.b_regmem.get.rec <> m_rec.io.b_out
  } else {
    io.b_port.get.rec <> m_rec.io.b_out
  }

  // ******************************
  //           INTERRUPT
  // ******************************
  io.o_irq_send := ~r_status.idle & w_idle

  io.o_irq_rec := false.B
  switch (w_config.irq) {
    is (IRQ.B1)   { io.o_irq_rec := m_rec.io.o_val(0).valid}
    is (IRQ.B2)   { io.o_irq_rec := m_rec.io.o_val(1).valid}
    is (IRQ.B4)   { io.o_irq_rec := m_rec.io.o_val(3).valid}
    is (IRQ.B8)   {
      if (p.nDataByte >= 8) {
                    io.o_irq_rec := m_rec.io.o_val(7).valid
      }      
    }
  }

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    
  }
}

object Ps2KeyboardCtrl extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Ps2KeyboardCtrl(Ps2ConfigBase), args)
}

object Ps2Keyboard extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Ps2Keyboard(Ps2ConfigBase), args)
}

