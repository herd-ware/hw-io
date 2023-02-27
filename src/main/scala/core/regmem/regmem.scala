/*
 * File: regmem.scala                                                          *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-27 06:21:41 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.core.regmem

import chisel3._
import chisel3.util._

import herd.common.bus._
import herd.common.gen._
import herd.common.dome._
import herd.common.mem.mb4s._
import herd.io.periph.uart._
import herd.io.periph.timer._


class RegMem (p: RegMemParams) extends Module {
  require((p.pPort.size == 1), "Only one port is available for RegMem.")
  require(((p.nDataBit == 32) || (p.nDataBit == 64)), "RegMem must have 32 or 64 data bits.")
  require((p.nCTimer <= CST.MAXCTIMER), "Only " + CST.MAXCTIMER + " core timers are possible.")
  require((p.nScratch <= CST.MAXSCRATCH), "Only " + CST.MAXSCRATCH + " scratch registers are possible.")

  val io = IO(new Bundle {       
    val b_dome = if (p.useDome) Some(Vec(p.nDome, new DomeIO(p.nAddrBit, p.nDataBit))) else None
     
    val i_slct = if (p.useDomeSlct) Some(Input(new SlctBus(p.nDome, p.nPart, 1))) else None  
    val b_port  = Flipped(new Mb4sIO(p.pPort(0)))

    val b_mtimer = if (!p.useChamp) Some(Flipped(new TimerRegMemIO())) else None
    val b_ltimer = if (p.useChamp) Some(Vec(p.nChampTrapLvl, Flipped(new TimerRegMemIO()))) else None
    val b_ctimer = if (p.nCTimer > 0) Some(Vec(p.nCTimer, Flipped(new TimerRegMemIO()))) else None
  })

  def isReqAddr(off: UInt): Bool = {
    val w_addr = Wire(UInt(p.nAddrBit.W))
    w_addr := BigInt(p.nAddrBase, 16).U + off
    return (w_req.ctrl.get.addr(COMMON.NBIT, 0) === w_addr(COMMON.NBIT, 0))
  }
  
  def isAckAddr(off: UInt): Bool = {
    val w_addr = Wire(UInt(p.nAddrBit.W))
    w_addr := BigInt(p.nAddrBase, 16).U + off
    return (w_ack.ctrl.get.addr(COMMON.NBIT, 0) === w_addr(COMMON.NBIT, 0))
  }

  val m_req = Module(new Mb4sReqSReg(p.pPort(0), p.useReqReg))
  val m_ack = Module(new GenSReg(p, new Mb4sReqBus(p.pPort(0)), UInt(p.nDataBit.W), false, false, true))
    
  val r_scratch = Reg(Vec(p.nScratch, UInt(32.W)))
  
  val w_wdata = Wire(UInt(p.nDataBit.W))
  
  // ******************************
  //         DOME INTERFACE
  // ******************************
  val r_slct_req = Reg(new SlctBus(p.nDome, p.nPart, 1))
  val r_slct_ack = Reg(new SlctBus(p.nDome, p.nPart, 1))

  val w_slct_req = Wire(new SlctBus(p.nDome, p.nPart, 1))
  val w_slct_ack = Wire(new SlctBus(p.nDome, p.nPart, 1))

  if (p.useDomeSlct) {
    if (p.useReqReg) {
      w_slct_req := r_slct_req
    } else {
      w_slct_req := io.i_slct.get
    }    

    r_slct_ack := w_slct_req
    w_slct_ack := r_slct_ack
  } else {    
    w_slct_req := SLCT.ZERO
    w_slct_ack := SLCT.ZERO
  }

  // ******************************
  //         DEFAULT I/Os
  // ******************************
  if (p.useChamp) {
    for (tl <- 0 until p.nChampTrapLvl) {
      for (w <- 0 until 6) {
        io.b_ltimer.get(tl).wen(w) := false.B
      }
      io.b_ltimer.get(tl).wdata := w_wdata
    }
  } else {
    for (w <- 0 until 6) {
      io.b_mtimer.get.wen(w) := false.B
    }
    io.b_mtimer.get.wdata := w_wdata  
  }

  for (ct <- 0 until p.nCTimer) {
    for (w <- 0 until 6) {
      io.b_ctimer.get(ct).wen(w) := false.B
    }
    io.b_ctimer.get(ct).wdata := w_wdata
  }

  // ******************************
  //              REQ
  // ******************************  
  val w_req = Wire(new GenSVBus(p, new Mb4sReqBus(p.pPort(0)), UInt(0.W)))

  val w_req_wait = Wire(Bool())  

  val w_req_wwait = Wire(Bool())
  val w_req_await = Wire(Bool())

  // ------------------------------
  //             PORT
  // ------------------------------
  m_req.io.b_port <> io.b_port.req
  if (p.useDomeSlct) m_req.io.i_slct.get := w_slct_req
  m_req.io.b_sout.ready := ~w_req_wait & ~w_req_await

  w_req.valid := m_req.io.b_sout.valid
  if (p.useDome) w_req.dome.get := m_req.io.b_sout.dome.get
  w_req.ctrl.get := m_req.io.b_sout.ctrl.get
  if (p.readOnly) w_req.ctrl.get.op := OP.R

  // ------------------------------
  //             WAIT
  // ------------------------------  
  if (p.readOnly) {
    w_req_wwait := false.B
  } else {
    w_req_wwait := m_ack.io.o_val.valid(w_slct_req.dome) & m_ack.io.o_val.ctrl.get(w_slct_req.dome).wa & w_req.ctrl.get.ra
  }  

  w_req_wait := w_req_wwait

  // ------------------------------
  //             READ
  // ------------------------------
  val w_rdata = Wire(UInt(32.W))

  w_rdata := DontCare
  when (w_req.valid & w_req.ctrl.get.ra) {
    // ''''''''''''''''''''''''''''''
    //            32 BITS
    // ''''''''''''''''''''''''''''''
    if (p.useChamp) {
      // Level 0 Timer
      if (p.nChampTrapLvl > 0) {
        when (isReqAddr(CHAMP.L0TIMER_STATUS.U)) {
          w_rdata := io.b_ltimer.get(0).status
        }
        when (isReqAddr(CHAMP.L0TIMER_CONFIG.U)) {
          w_rdata := io.b_ltimer.get(0).config
        }
        when (isReqAddr(CHAMP.L0TIMER_CNT.U)) {
          w_rdata := io.b_ltimer.get(0).cnt(31, 0)
        }
        when (isReqAddr(CHAMP.L0TIMER_CNTH.U)) {
          w_rdata := io.b_ltimer.get(0).cnt(63, 32)
        }
        when (isReqAddr(CHAMP.L0TIMER_CMP.U)) {
          w_rdata := io.b_ltimer.get(0).cmp(31, 0)
        }
        when (isReqAddr(CHAMP.L0TIMER_CMPH.U)) {
          w_rdata := io.b_ltimer.get(0).cmp(63, 32)
        }
      }

      // Level 1 Timer
      if (p.nChampTrapLvl > 1) {
        when (isReqAddr(CHAMP.L0TIMER_STATUS.U)) {
          w_rdata := io.b_ltimer.get(1).status
        }
        when (isReqAddr(CHAMP.L0TIMER_CONFIG.U)) {
          w_rdata := io.b_ltimer.get(1).config
        }
        when (isReqAddr(CHAMP.L1TIMER_CNT.U)) {
          w_rdata := io.b_ltimer.get(1).cnt(31, 0)
        }
        when (isReqAddr(CHAMP.L1TIMER_CNTH.U)) {
          w_rdata := io.b_ltimer.get(1).cnt(63, 32)
        }
        when (isReqAddr(CHAMP.L1TIMER_CMP.U)) {
          w_rdata := io.b_ltimer.get(1).cmp(31, 0)
        }
        when (isReqAddr(CHAMP.L1TIMER_CMPH.U)) {
          w_rdata := io.b_ltimer.get(1).cmp(63, 32)
        }
      }
    } else {
      // Machine Timer
      when (isReqAddr(PRIV.MTIMER_STATUS.U)) {
        w_rdata := io.b_mtimer.get.status
      }
      when (isReqAddr(PRIV.MTIMER_CONFIG.U)) {
        w_rdata := io.b_mtimer.get.config
      }
      when (isReqAddr(PRIV.MTIMER_CNT.U)) {
        w_rdata := io.b_mtimer.get.cnt(31, 0)
      }
      when (isReqAddr(PRIV.MTIMER_CNTH.U)) {
        w_rdata := io.b_mtimer.get.cnt(63, 32)
      }
      when (isReqAddr(PRIV.MTIMER_CMP.U)) {
        w_rdata := io.b_mtimer.get.cmp(31, 0)
      }
      when (isReqAddr(PRIV.MTIMER_CMPH.U)) {
        w_rdata := io.b_mtimer.get.cmp(63, 32)
      } 
    }

    // CTimer
    for (ct <- 0 until p.nCTimer) {
      when (isReqAddr(COMMON.CTIMER_STATUS.U + (ct * COMMON.CTIMER_NBYTE).U)) {
        w_rdata := io.b_ctimer.get(ct).status
      }
      when (isReqAddr(COMMON.CTIMER_CONFIG.U + (ct * COMMON.CTIMER_NBYTE).U)) {
        w_rdata := io.b_ctimer.get(ct).config
      }
      when (isReqAddr(COMMON.CTIMER_CNT.U + (ct * COMMON.CTIMER_NBYTE).U)) {
        w_rdata := io.b_ctimer.get(ct).cnt(31, 0)
      }
      when (isReqAddr(COMMON.CTIMER_CNTH.U + (ct * COMMON.CTIMER_NBYTE).U)) {
        w_rdata := io.b_ctimer.get(ct).cnt(63, 32)
      }
      when (isReqAddr(COMMON.CTIMER_CMP.U + (ct * COMMON.CTIMER_NBYTE).U)) {
        w_rdata := io.b_ctimer.get(ct).cmp(31, 0)
      }
      when (isReqAddr(COMMON.CTIMER_CMPH.U + (ct * COMMON.CTIMER_NBYTE).U)) {
        w_rdata := io.b_ctimer.get(ct).cmp(63, 32)
      }
    }

    // Scratch
    for (s <- 0 until p.nScratch) {
      when (isReqAddr(COMMON.SCRATCH.U + (s * 4).U)) {
        w_rdata := r_scratch(s)
      }
    }

    // ''''''''''''''''''''''''''''''
    //            64 BITS
    // ''''''''''''''''''''''''''''''
    if (p.nDataBit == 64) {
      when (w_req.ctrl.get.size === SIZE.B8.U) {
        if (p.useChamp) {
          // Level 0 Timer
          if (p.nChampTrapLvl > 0) {
            when (isReqAddr(CHAMP.L0TIMER_CNT.U)) {
              w_rdata := io.b_ltimer.get(0).cnt
            }
            when (isReqAddr(CHAMP.L0TIMER_CMP.U)) {
              w_rdata := io.b_ltimer.get(0).cmp
            }
          }

          // Level 1 Timer
          if (p.nChampTrapLvl > 1) {
            when (isReqAddr(CHAMP.L1TIMER_CNT.U)) {
              w_rdata := io.b_ltimer.get(1).cnt
            }
            when (isReqAddr(CHAMP.L1TIMER_CMP.U)) {
              w_rdata := io.b_ltimer.get(1).cmp
            }
          }
        } else {
          // Machine Timer
          when (isReqAddr(PRIV.MTIMER_CNT.U)) {
            w_rdata := io.b_mtimer.get.cnt
          }
          when (isReqAddr(PRIV.MTIMER_CMP.U)) {
            w_rdata := io.b_mtimer.get.cmp
          }      
        }

        // CTimer
        for (ct <- 0 until p.nCTimer) {
          when (isReqAddr(COMMON.CTIMER_CNT.U + (ct * COMMON.CTIMER_NBYTE).U)) {
            w_rdata := io.b_ctimer.get(ct).cnt
          }
          when (isReqAddr(COMMON.CTIMER_CMP.U + (ct * COMMON.CTIMER_NBYTE).U)) {
            w_rdata := io.b_ctimer.get(ct).cmp
          }          
        }

        // Scratch
        for (s <- 0 until (p.nScratch / 2)) {
          when (isReqAddr(COMMON.SCRATCH.U + (s * 8).U)) {
            w_rdata := Cat(r_scratch(s * 2 + 1), r_scratch(s * 2))
          }
        }
      }
    }
  }

  // ******************************
  //             ACK
  // ******************************  
  val w_ack = Wire(new GenSVBus(p, new Mb4sReqBus(p.pPort(0)), UInt(p.nDataBit.W)))  

  val w_ack_pwait = Wire(Bool())
  val w_mb4s_wwait = Wire(Bool())
  val w_mb4s_rwait = Wire(Bool())
  
  // ------------------------------
  //           REGISTER
  // ------------------------------  
  for (ds <- 0 until p.nDomeSlct) {
    m_ack.io.i_flush(ds) := false.B
  }

  w_req_await := ~m_ack.io.b_sin.ready
  if (p.useDomeSlct) m_ack.io.i_slct_in.get := w_slct_req
  m_ack.io.b_sin.valid := w_req.valid & ~w_req_wait
  if (p.useDome) m_ack.io.b_sin.dome.get := w_req.dome.get
  m_ack.io.b_sin.ctrl.get := w_req.ctrl.get
  m_ack.io.b_sin.data.get := w_rdata

  m_ack.io.b_sout.ready := ~w_ack_pwait
  if (p.useDomeSlct) m_ack.io.i_slct_out.get := w_slct_ack
  w_ack.valid := m_ack.io.b_sout.valid
  if (p.useDome) w_ack.dome.get := m_ack.io.b_sout.dome.get
  w_ack.ctrl.get := m_ack.io.b_sout.ctrl.get
  w_ack.data.get := m_ack.io.b_sout.data.get

  // ------------------------------
  //             PORT
  // ------------------------------
  val m_wmb4s = if (!p.readOnly) Some(Module(new Mb4sDataSReg(p.pPort(0)))) else None

  // Write
  if (!p.readOnly) {
    m_wmb4s.get.io.b_port <> io.b_port.write

    if (p.useDomeSlct) m_wmb4s.get.io.i_slct.get := w_slct_ack
    m_wmb4s.get.io.b_sout.ready := w_ack.valid & w_ack.ctrl.get.wa

    w_wdata := m_wmb4s.get.io.b_sout.data.get

  } else {
    for (ds <- 0 until p.nDomeSlct) {
      io.b_port.write.ready(ds) := false.B
    }
    w_wdata := DontCare
  }

  // Read
  io.b_port.read.valid := w_ack.valid & w_ack.ctrl.get.ra
  if (p.useDomeSlct) io.b_port.read.dome.get := w_slct_ack.dome else if (p.useDome) io.b_port.read.dome.get := w_ack.dome.get
  io.b_port.read.data := w_ack.data.get 

  // ------------------------------
  //            WRITE
  // ------------------------------
  if (!p.readOnly) {
    when (w_ack.valid & w_ack.ctrl.get.wo & ~w_ack_pwait) {
      // ''''''''''''''''''''''''''''''
      //            32 BITS
      // ''''''''''''''''''''''''''''''
      if (p.useChamp) {
        // Level 0 Timer
        if (p.nChampTrapLvl > 0) {
          when (io.b_dome.get(w_ack.dome.get).tl(0)) {
            io.b_ltimer.get(0).wen(1) := (isAckAddr(CHAMP.L0TIMER_CONFIG.U))
            io.b_ltimer.get(0).wen(2) := (isAckAddr(CHAMP.L0TIMER_CNT.U))
            io.b_ltimer.get(0).wen(3) := (isAckAddr(CHAMP.L0TIMER_CNTH.U))
            io.b_ltimer.get(0).wen(4) := (isAckAddr(CHAMP.L0TIMER_CMP.U))
            io.b_ltimer.get(0).wen(5) := (isAckAddr(CHAMP.L0TIMER_CMPH.U))
          }
        }

        // Level 1 Timer
        if (p.nChampTrapLvl > 1) {
          when (io.b_dome.get(w_ack.dome.get).tl(1)) {
            io.b_ltimer.get(1).wen(1) := (isAckAddr(CHAMP.L1TIMER_CONFIG.U))
            io.b_ltimer.get(1).wen(2) := (isAckAddr(CHAMP.L1TIMER_CNT.U))
            io.b_ltimer.get(1).wen(3) := (isAckAddr(CHAMP.L1TIMER_CNTH.U))
            io.b_ltimer.get(1).wen(4) := (isAckAddr(CHAMP.L1TIMER_CMP.U))
            io.b_ltimer.get(1).wen(5) := (isAckAddr(CHAMP.L1TIMER_CMPH.U))
          }
        }  
      } else {
        // Machine Timer
        io.b_mtimer.get.wen(1) := (isAckAddr(PRIV.MTIMER_CONFIG.U))
        io.b_mtimer.get.wen(2) := (isAckAddr(PRIV.MTIMER_CNT.U))
        io.b_mtimer.get.wen(3) := (isAckAddr(PRIV.MTIMER_CNTH.U))
        io.b_mtimer.get.wen(4) := (isAckAddr(PRIV.MTIMER_CMP.U))
        io.b_mtimer.get.wen(5) := (isAckAddr(PRIV.MTIMER_CMPH.U)) 
      }

      // CTimer
      for (ct <- 0 until p.nCTimer) {
        io.b_ctimer.get(ct).wen(1) := (isAckAddr(COMMON.CTIMER_CONFIG.U + (ct * COMMON.CTIMER_NBYTE).U))
        io.b_ctimer.get(ct).wen(2) := (isAckAddr(COMMON.CTIMER_CNT.U + (ct * COMMON.CTIMER_NBYTE).U))
        io.b_ctimer.get(ct).wen(3) := (isAckAddr(COMMON.CTIMER_CNTH.U + (ct * COMMON.CTIMER_NBYTE).U))
        io.b_ctimer.get(ct).wen(4) := (isAckAddr(COMMON.CTIMER_CMP.U + (ct * COMMON.CTIMER_NBYTE).U))
        io.b_ctimer.get(ct).wen(5) := (isAckAddr(COMMON.CTIMER_CMPH.U + (ct * COMMON.CTIMER_NBYTE).U))
      }

      // Scratch
      for (s <- 0 until p.nScratch) {
        when (isAckAddr(COMMON.SCRATCH.U + (s * 4).U)) {
          r_scratch(s) := w_wdata(31, 0)
        }
      }

      // ''''''''''''''''''''''''''''''
      //            64 BITS
      // ''''''''''''''''''''''''''''''
      if (p.nDataBit == 64) {
        when (w_ack.ctrl.get.size === SIZE.B8.U) {
          if (p.useChamp) {
            // Level 0 Timer
            if (p.nChampTrapLvl > 0) {
              when (io.b_dome.get(w_ack.dome.get).tl(0)) {
                io.b_ltimer.get(0).wen(2) := (isAckAddr(CHAMP.L0TIMER_CNT.U))
                io.b_ltimer.get(0).wen(4) := (isAckAddr(CHAMP.L0TIMER_CMP.U))
              }
            }

            // Level 1 Timer
            if (p.nChampTrapLvl > 0) {
              when (io.b_dome.get(w_ack.dome.get).tl(1)) {
                io.b_ltimer.get(1).wen(2) := (isAckAddr(CHAMP.L1TIMER_CNT.U))
                io.b_ltimer.get(1).wen(4) := (isAckAddr(CHAMP.L1TIMER_CMP.U))
              }
            }
          } else {
            // Machine Timer
            io.b_mtimer.get.wen(2) := (isAckAddr(PRIV.MTIMER_CNT.U))
            io.b_mtimer.get.wen(4) := (isAckAddr(PRIV.MTIMER_CMP.U)) 
          }

          // CTimer
          for (ct <- 0 until p.nCTimer) {
            io.b_ctimer.get(ct).wen(2) := (isAckAddr(COMMON.CTIMER_CNT.U + (ct * COMMON.CTIMER_NBYTE).U))
            io.b_ctimer.get(ct).wen(4) := (isAckAddr(COMMON.CTIMER_CMP.U + (ct * COMMON.CTIMER_NBYTE).U))
          }

          // Scratch
          for (s <- 0 until (p.nScratch / 2)) {
            when (isAckAddr(COMMON.SCRATCH.U + (s * 8).U)) {
              r_scratch(s * 2 + 1) := w_wdata(63, 32)
            }
          }
        }
      }

    }
  }

  // ------------------------------
  //             WAIT
  // ------------------------------
  if (!p.readOnly) w_mb4s_wwait := w_ack.ctrl.get.wa & ~m_wmb4s.get.io.b_sout.valid else w_mb4s_wwait := false.B
  w_mb4s_rwait := w_ack.ctrl.get.ra & ~io.b_port.read.ready(w_slct_ack.dome) 

  w_ack_pwait := w_ack.valid & (w_mb4s_wwait | w_mb4s_rwait)

  // ******************************
  //              I/Os
  // ******************************

  // ******************************
  //              DOME
  // ******************************
  if (p.useDome) {
    for (d <- 0 until p.nDome) {
      io.b_dome.get(d).free := (~m_req.io.b_sout.valid | (d.U =/= m_req.io.b_sout.dome.get)) & (~m_req.io.b_sout.valid | (d.U =/= m_req.io.b_sout.dome.get))
    }
  }

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    dontTouch(io.b_port)
  } 
}

object RegMem extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new RegMem(RegMemConfigBase), args)
}


