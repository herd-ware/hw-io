/*
 * File: spi_flash.scala                                                       *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-04-03 10:11:10 am                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.periph.spi_flash

import chisel3._
import chisel3.util._

import herd.common.gen._
import herd.common.tools.{Counter}
import herd.io.periph.spi._


class SpiFlashCtrl (p: SpiFlashParams) extends Module {
  import herd.io.periph.spi_flash.SpiFlashFSM._

  val io = IO(new Bundle {
    val o_idle = Output(Bool())
    val i_config = Input(new SpiFlashConfigBus(p))

    val b_req = Flipped(new GenRVIO(p, new SpiFlashCtrlBus(), UInt(8.W)))
    val b_read = new GenRVIO(p, Bool(), UInt(8.W))

    val b_spi = new SpiIO(1)
  })

  val r_fsm = RegInit(s0IDLE)
  val r_config = Reg(new SpiFlashConfigBus(p))
  val r_ctrl = Reg(new SpiFlashCtrlBus())
  
  val r_csn = RegInit(1.B)
  val r_sclk = RegInit(0.B)
  val r_rdata = Reg(UInt(8.W))
  val r_wdata = Reg(UInt(8.W))

  val w_addr = Wire(Vec(3, UInt(8.W)))

  for (b <- 0 until 3) {
    w_addr(b) := r_config.addr(((3 - b) * 8) - 1, ((2 - b) * 8))
  }

  // ******************************
  //            COUNTERS
  // ******************************
  val m_ccnt = Module(new Counter(32))
  val m_bcnt = Module(new Counter(3))
  val m_dcnt = Module(new Counter(p.nAddrBit))

  m_ccnt.io.i_limit := r_config.cycle
  m_ccnt.io.i_init := (r_fsm === s0IDLE)
  m_ccnt.io.i_en := (r_fsm =/= s0IDLE)

  m_bcnt.io.i_limit := 0.U
  m_bcnt.io.i_init := true.B
  m_bcnt.io.i_en := false.B

  m_dcnt.io.i_limit := r_config.offset
  m_dcnt.io.i_init := true.B
  m_dcnt.io.i_en := false.B

  // ******************************
  //             SCLK
  // ******************************
  val w_half = (m_ccnt.io.o_val === ((r_config.cycle >> 1.U) - 1.U))
  val w_full = (m_ccnt.io.o_val === (r_config.cycle - 1.U))

  when (r_fsm =/= s0IDLE) {
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
        r_config := io.i_config
        r_ctrl := io.b_req.ctrl.get
        when (io.b_req.ctrl.get.cmd === CMD.RA) {
          r_fsm := s1ACMD
        }.otherwise {
          r_fsm := s4DATA
        }        

        r_csn := false.B     
      }
    }

    // ------------------------------
    //         AUTO: COMMAND
    // ------------------------------
    is (s1ACMD) {
      m_bcnt.io.i_init := false.B
      m_bcnt.io.i_en := false.B

      when (m_ccnt.io.o_flag){
        when (m_bcnt.io.o_flag) {
          r_fsm := s2AADDR
          m_bcnt.io.i_init := true.B
        }.otherwise {
          m_bcnt.io.i_en := true.B
        }        
      }
    }

    // ------------------------------
    //         AUTO: ADDRESS
    // ------------------------------
    is (s2AADDR) {
      m_bcnt.io.i_init := false.B
      m_bcnt.io.i_en := false.B
      m_dcnt.io.i_limit := 3.U
      m_dcnt.io.i_init := false.B
      m_dcnt.io.i_en := false.B

      when (m_ccnt.io.o_flag){
        m_ccnt.io.i_init := true.B
        when (m_bcnt.io.o_flag) {
          m_bcnt.io.i_init := true.B
          when (m_dcnt.io.o_flag) {
            m_dcnt.io.i_init := true.B
            r_fsm := s3AREAD
          }.otherwise {
            m_dcnt.io.i_en := true.B
          }          
        }.otherwise {
          m_bcnt.io.i_en := true.B
        }        
      }
    }

    // ------------------------------
    //          AUTO: READ
    // ------------------------------
    is (s3AREAD) {
      m_bcnt.io.i_init := false.B
      m_bcnt.io.i_en := false.B
      m_dcnt.io.i_init := false.B
      m_dcnt.io.i_en := false.B

      when (m_ccnt.io.o_flag){
        m_ccnt.io.i_init := true.B
        when (m_bcnt.io.o_flag) {
          m_bcnt.io.i_init := true.B
          when (m_dcnt.io.o_flag) {
            m_dcnt.io.i_init := true.B
            r_fsm := s0IDLE
            r_csn := true.B  
          }.otherwise {
            m_dcnt.io.i_en := true.B
          }          
        }.otherwise {
          m_bcnt.io.i_en := true.B
        }        
      }
    }

    // ------------------------------
    //             DATA
    // ------------------------------
    is (s4DATA) {
      m_bcnt.io.i_init := false.B
      m_bcnt.io.i_en := false.B

      when (m_ccnt.io.o_flag) {
        when (m_bcnt.io.o_flag) {
          m_bcnt.io.i_init := true.B
          when (~io.b_req.valid | ~io.b_req.ctrl.get.mb) {
            r_fsm := s0IDLE
            r_csn := true.B  
          }
        }.otherwise {
          m_bcnt.io.i_en := true.B
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
        when (io.b_req.ctrl.get.cmd === CMD.RA) {
          r_wdata := Reverse(FLASH.READ) 
        }.otherwise {
          r_wdata := Reverse(io.b_req.data.get) 
        }             
      }
    }

    // ------------------------------
    //         AUTO: COMMAND
    // ------------------------------
    is (s1ACMD) {
      when (m_ccnt.io.o_flag) {
        when (m_bcnt.io.o_flag) {
          r_wdata := Reverse(w_addr(0))
        }.otherwise {
          switch (r_config.mode) {
            is (MODE.BASE) {  r_wdata := (r_wdata >> 1.U)}
            is (MODE.DUAL) {  r_wdata := (r_wdata >> 2.U)}
            is (MODE.QUAD) {  r_wdata := (r_wdata >> 4.U)}
          }        
        }
      }
    }

    // ------------------------------
    //         AUTO: ADDRESS
    // ------------------------------
    is (s2AADDR) {
      when (m_ccnt.io.o_flag) {
        when (m_bcnt.io.o_flag) {
          r_wdata := Reverse(w_addr(m_dcnt.io.o_val + 1.U))
        }.otherwise {
          switch (r_config.mode) {
            is (MODE.BASE) {  r_wdata := (r_wdata >> 1.U)}
            is (MODE.DUAL) {  r_wdata := (r_wdata >> 2.U)}
            is (MODE.QUAD) {  r_wdata := (r_wdata >> 4.U)}
          }        
        }
      }
    }

    // ------------------------------
    //           AUTO: READ
    // ------------------------------
    is (s3AREAD) {
      when (w_half) {
        switch (r_config.mode) {
          is (MODE.BASE) {  r_rdata := Cat(r_rdata(6, 0), io.b_spi.data.in(1))}
          is (MODE.DUAL) {  r_rdata := Cat(r_rdata(5, 0), io.b_spi.data.in(1, 0))}
          is (MODE.QUAD) {  r_rdata := Cat(r_rdata(3, 0), io.b_spi.data.in(3, 0))}
        }
      }
    }

    // ------------------------------
    //             DATA
    // ------------------------------
    is (s4DATA) {
      when (m_ccnt.io.o_flag) {
        when (m_bcnt.io.o_flag & (~io.b_req.valid | ~io.b_req.ctrl.get.mb)) {
          r_wdata := Reverse(io.b_req.data.get)
        }.otherwise {
          switch (r_config.mode) {
            is (MODE.BASE) {  r_wdata := (r_wdata >> 1.U)}
            is (MODE.DUAL) {  r_wdata := (r_wdata >> 2.U)}
            is (MODE.QUAD) {  r_wdata := (r_wdata >> 4.U)}
          }
        }

        when (w_half) {
          switch (r_config.mode) {
            is (MODE.BASE) {  r_rdata := Cat(r_rdata(6, 0), io.b_spi.data.in(1))}
            is (MODE.DUAL) {  r_rdata := Cat(r_rdata(5, 0), io.b_spi.data.in(1, 0))}
            is (MODE.QUAD) {  r_rdata := Cat(r_rdata(3, 0), io.b_spi.data.in(3, 0))}
          }
        }
      }
    }
  }

  // ******************************
  //              I/Os
  // ******************************
  io.o_idle := false.B
  io.b_req.ready := false.B
  io.b_read.valid := false.B
  io.b_read.ctrl.get := r_ctrl.dma
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
    //         AUTO: COMMAND
    // ------------------------------
    is (s1ACMD) {
    }

    // ------------------------------
    //         AUTO: ADDRESS
    // ------------------------------
    is (s2AADDR) {
    }

    // ------------------------------
    //          AUTO: READ
    // ------------------------------
    is (s3AREAD) {
      io.b_read.valid := m_ccnt.io.o_flag & m_bcnt.io.o_flag
    }

    // ------------------------------
    //             DATA
    // ------------------------------
    is (s4DATA) {
      io.b_req.ready := io.b_req.ctrl.get.mb & m_ccnt.io.o_flag & m_bcnt.io.o_flag
      io.b_read.valid := m_ccnt.io.o_flag & m_bcnt.io.o_flag & ((r_ctrl.cmd === CMD.R) | (r_ctrl.cmd === CMD.RW))
    }
  }

  io.b_spi.csn(0) := r_csn
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

class SpiFlash (p: SpiFlashParams) extends Module {  
  val io = IO(new Bundle {
    val o_irq_req = Output(Bool())
    val o_irq_read = Output(Bool())    

    val b_regmem = if (p.useRegMem) Some(new SpiFlashRegMemIO(p, p.nDataByte)) else None   
    val b_dma = if (p.useRegMem && p.useDma) Some(new SpiFlashAutoIO(p, p.nAddrBit, p.nDataByte)) else None

    val o_status = if (!p.useRegMem) Some(Output(new SpiFlashStatusBus(p))) else None
    val i_config = if (!p.useRegMem) Some(Input(new SpiFlashConfigBus(p))) else None
    val b_port = if (!p.useRegMem) Some(new SpiFlashPortIO(p, p.nDataByte)) else None

    val b_spi = new SpiIO(1)
  })

  val m_creq = Module(new GenFifo(p, new SpiFlashCtrlBus(), UInt(0.W), 4, p.nBufferDepth, p.nDataByte, 1))
  val m_dreq = Module(new GenFifo(p, UInt(0.W), UInt(8.W), 4, p.nBufferDepth, p.nDataByte, 1))
  val m_spi = Module(new SpiFlashCtrl(p))
  val m_read = Module(new GenFifo(p, Bool(), UInt(8.W), 4, p.nBufferDepth, 1, p.nDataByte))

  val r_status = Reg(new SpiFlashStatusBus(p))
  val r_config = Reg(new SpiFlashConfigBus(p))

  val w_status = Wire(new SpiFlashStatusBus(p))
  val w_config = Wire(new SpiFlashConfigBus(p))
  val w_idle = Wire(Bool())  

  val w_dma_req_valid = Wire(Bool())
  val w_dma_req_ready = Wire(Bool())
  val w_auto_req_valid = Wire(Bool())
  val w_auto_req_ready = Wire(Bool())

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

  w_idle := ~m_creq.io.b_out(0).valid & ~m_dreq.io.b_out(0).valid & m_spi.io.o_idle & ~m_read.io.b_out(0).valid

  // ------------------------------
  //             READ
  // ------------------------------ 
  if (p.useRegMem) {
    io.b_regmem.get.status := Cat(  0.U(8.W), 
                                    0.U(4.W), w_status.av.asUInt,
                                    0.U(4.W), w_status.full.asUInt,
                                    0.U(2.W), r_status.dma, r_status.auto, 0.U(3.W), w_status.idle)
  } else {
    io.o_status.get := r_status
  }

  // ------------------------------
  //             WRITE
  // ------------------------------ 
  r_status.idle := w_idle
  r_status.auto := (r_status.auto & ~w_idle) | (w_auto_req_valid & ~w_dma_req_valid & w_idle)
  r_status.dma := (r_status.dma & ~w_idle) | (w_dma_req_valid & w_idle)

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
                                    0.U(3.W), r_config.mode, 0.U(2.W), r_config.en)
    io.b_regmem.get.cycle := r_config.cycle
    io.b_regmem.get.addr := r_config.addr
    io.b_regmem.get.offset := r_config.offset
  }

  // ------------------------------
  //             WRITE
  // ------------------------------ 
  r_config.auto := r_config.auto & ~w_auto_req_ready
  if (p.useRegMem) {
    when (io.b_regmem.get.wen(1)) {
      r_config.en := io.b_regmem.get.wdata(0)
      r_config.mode := io.b_regmem.get.wdata(4, 3)
      r_config.auto := io.b_regmem.get.wdata(16)
      r_config.irq := io.b_regmem.get.wdata(24 + IRQ.NBIT - 1, 24)
    }
  
    when (io.b_regmem.get.wen(2)) {
      r_config.cycle := io.b_regmem.get.wdata
    }  
  
    when (io.b_regmem.get.wen(3)) {
      r_config.addr := io.b_regmem.get.wdata
    }.elsewhen(m_spi.io.b_read.valid & r_status.auto) {
      r_config.addr := r_config.addr + 1.U
    } 
  
    when (io.b_regmem.get.wen(4)) {
      r_config.offset := io.b_regmem.get.wdata
    } 

    if (p.useDma) {
      when (r_status.idle & io.b_dma.get.req.valid) {
        r_config.addr := io.b_dma.get.req.ctrl.get.addr
        r_config.offset := io.b_dma.get.req.ctrl.get.offset
      }
    }
  } else {
    r_config := io.i_config.get
  }

  // ******************************
  //            MASTER
  // ******************************
  // ------------------------------
  //           REQUEST
  // ------------------------------
  // Optional DMA
  if (p.useRegMem && p.useDma) {
    w_dma_req_valid := io.b_dma.get.req.valid
    io.b_dma.get.req.ready := w_dma_req_ready
  } else {
    w_dma_req_valid := false.B
    w_dma_req_ready := false.B
  }

  // Auto read
  w_auto_req_valid := r_config.auto
  w_auto_req_ready := r_status.idle & ~w_dma_req_valid

  // Flush
  m_creq.io.i_flush := false.B
  m_dreq.io.i_flush := false.B

  // Memory register
  if (p.useRegMem) {
    // Default
    m_dreq.io.b_in <> io.b_regmem.get.dreq
    for (b <- 0 until p.nDataByte) {
      m_creq.io.b_in(b).valid := false.B
      m_creq.io.b_in(b).ctrl.get.cmd := io.b_regmem.get.creq(b).ctrl.get(CMD.NBIT - 1, 0)
      m_creq.io.b_in(b).ctrl.get.mb := io.b_regmem.get.creq(b).ctrl.get(7)
      m_creq.io.b_in(b).ctrl.get.auto := false.B  
      m_creq.io.b_in(b).ctrl.get.dma := false.B  
      m_dreq.io.b_in(b).valid := false.B
      io.b_regmem.get.creq(b).ready := false.B
      io.b_regmem.get.dreq(b).ready := false.B
    }
    w_dma_req_ready := r_status.idle
    w_auto_req_ready := r_status.idle & ~w_dma_req_valid

    // DMA & auto
    when (r_status.idle & (w_dma_req_valid | w_auto_req_valid)) {
      m_creq.io.b_in(0).valid := true.B
      m_creq.io.b_in(0).ctrl.get.cmd := CMD.RA
      m_creq.io.b_in(0).ctrl.get.mb := true.B
      m_creq.io.b_in(0).ctrl.get.auto := w_auto_req_valid
      m_creq.io.b_in(0).ctrl.get.dma := w_dma_req_valid
      
    // Normal
    }.otherwise {
      for (b <- 0 until p.nDataByte) {
        m_creq.io.b_in(b).valid := io.b_regmem.get.creq(b).valid
        io.b_regmem.get.creq(b).ready := m_creq.io.b_in(b).ready
        m_dreq.io.b_in(b).valid := io.b_regmem.get.dreq(b).valid
        io.b_regmem.get.dreq(b).ready := m_dreq.io.b_in(b).ready
      }
    }
  // General config
  } else {
    m_creq.io.b_in <> io.b_port.get.creq
    m_dreq.io.b_in <> io.b_port.get.dreq
    w_auto_req_ready := false.B
    w_auto_req_ready := false.B
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
    if (p.useDma) {
      io.b_dma.get.read <> m_read.io.b_out

      for (b <- 0 until p.nDataByte) {
        when (r_status.dma) {
          io.b_regmem.get.read(b).valid := false.B
          io.b_dma.get.read(b) <> m_read.io.b_out(b)
        }.otherwise {
          io.b_dma.get.read(b).valid := false.B
          io.b_regmem.get.read(b) <> m_read.io.b_out(b)
        }
      }
    }
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

object SpiFlashCtrl extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new SpiFlashCtrl(SpiFlashConfigBase), args)
}

object SpiFlash extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new SpiFlash(SpiFlashConfigBase), args)
}

