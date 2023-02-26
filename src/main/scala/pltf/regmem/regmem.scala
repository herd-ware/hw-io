/*
 * File: regmem.scala                                                          *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:11:21 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.pltf.regmem

import chisel3._
import chisel3.util._

import herd.common.bus._
import herd.common.gen._
import herd.common.dome._
import herd.common.mem.mb4s._
import herd.io.periph.gpio._
import herd.io.periph.uart._
import herd.io.periph.timer._
import herd.io.periph.spi._
import herd.io.periph.spi_flash._
import herd.io.periph.ps2._
import herd.io.periph.i2c._
import herd.io.pltf.plic._


class RegMem (p: RegMemParams) extends Module {
  require((p.pPort.size == 1), "Only one port is available for RegMem.")
  require(((p.nDataBit == 32) || (p.nDataBit == 64)), "RegMem must have 32 or 64 data bits.")
  require((p.nGpio <= (COMMON.GPIOA_MAX * 32)), "Only " + (COMMON.GPIOA_MAX * 32) + " GPIOs are possible.")
  require((p.nPTimer <= COMMON.PTIMER0_MAX), "Only " + COMMON.PTIMER0_MAX + " platform timers are possible.")
  require((p.nUart <= COMMON.UART0_MAX), "Only " + COMMON.UART0_MAX + " UART ports are possible.")
  require((p.nSpi <= COMMON.SPI0_MAX), "Only " + COMMON.SPI0_MAX + " SPI ports are possible.")
  require((p.nI2c <= COMMON.I2C0_MAX), "Only " + COMMON.I2C0_MAX + " I2C ports are possible.")

  val io = IO(new Bundle {        
    val b_dome = if (p.useDome) Some(Vec(p.nDome, new DomeIO(p.nAddrBit, p.nDataBit))) else None  

    val i_slct = if (p.useDomeSlct) Some(Input(new SlctBus(p.nDome, p.nPart, 1))) else None  
    val b_port  = Flipped(new Mb4sIO(p.pPort(0)))

    val b_plic = Flipped(new PlicRegMemIO(p))
    val b_gpio = if (p.nGpio > 0) Some(Flipped(new GpioRegMemIO(p.nGpio32b))) else None
    val b_ptimer = if (p.nPTimer > 0) Some(Vec(p.nPTimer, Flipped(new TimerRegMemIO()))) else None
    val b_spi_flash = if (p.useSpiFlash) Some(Flipped(new SpiFlashRegMemIO(p, p.nDataByte))) else None
    val b_ps2_kb = if (p.usePs2Keyboard) Some(Flipped(new Ps2KeyboardRegMemIO(p, p.nDataByte))) else None
    val b_uart = if (p.nUart > 0) Some(Vec(p.nUart, Flipped(new UartRegMemIO(p, p.nDataByte)))) else None
    val b_spi = if (p.nSpi > 0) Some(Vec(p.nSpi, Flipped(new SpiRegMemIO(p, p.nDataByte)))) else None
    val b_i2c = if (p.nI2c > 0) Some(Vec(p.nI2c, Flipped(new I2cRegMemIO(p, p.nDataByte)))) else None

    val o_irq_msi = if (!p.useDome) Some(Output(Vec((p.nHart32b * 32), Bool()))) else None
    val o_irq_ssi = if (!p.useDome) Some(Output(Vec((p.nHart32b * 32), Bool()))) else None
    val o_irq_lsi = if (p.useDome) Some(Output(Vec(p.nCepsTrapLvl, Vec((p.nHart32b * 32), Bool())))) else None
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

  val init_lsi = Wire(Vec(p.nCepsTrapLvl, Vec(p.nHart32b, UInt(32.W))))

  for (tl <- 0 until p.nCepsTrapLvl) {
    for (hw <- 0 until p.nHart32b) {
      init_lsi(tl)(hw) := 0.U
    }
  }

  val r_msi = RegInit(VecInit(Seq.fill(p.nHart32b)(0.U(32.W))))
  val r_ssi = RegInit(VecInit(Seq.fill(p.nHart32b)(0.U(32.W))))
  val r_lsi = RegInit(init_lsi)

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
  // PLIC
  for (ca <- 0 until p.nPlicCause) {
    io.b_plic.wsip(ca) := false.B
    io.b_plic.wattr(ca) := false.B
  }
  for (ca <- 0 until p.nPlicCause32b) {
    io.b_plic.wip(ca) := false.B
  }
  for (co <- 0 until p.nPlicContext) {
    for (ca <- 0 until p.nPlicCause32b) {
      io.b_plic.wen(co)(ca) := false.B
    }
    for (b <- 0 until 2) {
      io.b_plic.wcfg(co)(b) := false.B
    }
  }
  io.b_plic.wdata := w_wdata

  // GPIO
  if (p.nGpio > 0) {
    for (g32 <- 0 until (p.nGpio32b * 2)) {
      io.b_gpio.get.wen(g32) := false.B
    }
    io.b_gpio.get.wdata := w_wdata
  }

  // PTimer
  for (pt <- 0 until p.nPTimer) {
    for (w <- 0 until 6) {
      io.b_ptimer.get(pt).wen(w) := false.B
    }
    io.b_ptimer.get(pt).wdata := w_wdata
  }

  // SPI Flash
  if (p.useSpiFlash) {
    for (w <- 0 until 5) {
      io.b_spi_flash.get.wen(w) := false.B
    }
    io.b_spi_flash.get.wdata := w_wdata

    for (b <- 0 until p.nDataByte) {
      io.b_spi_flash.get.creq(b).valid := false.B
      io.b_spi_flash.get.creq(b).ctrl.get := w_wdata((b + 1) * 8 - 1, b * 8)
      io.b_spi_flash.get.dreq(b).valid := false.B
      io.b_spi_flash.get.dreq(b).data.get := w_wdata((b + 1) * 8 - 1, b * 8)
      io.b_spi_flash.get.read(b).ready := false.B
    }
  }

  // PS/2 Keyboard
  if (p.usePs2Keyboard) {
    for (w <- 0 until 3) {
      io.b_ps2_kb.get.wen(w) := false.B
    }
    io.b_ps2_kb.get.wdata := w_wdata

    for (b <- 0 until p.nDataByte) {
      io.b_ps2_kb.get.send(b).valid := false.B
      io.b_ps2_kb.get.send(b).data.get := w_wdata((b + 1) * 8 - 1, b * 8)
      io.b_ps2_kb.get.rec(b).ready := false.B
    }
  }

  // UART
  if (p.nUart > 0) {
    for (u <- 0 until p.nUart) {
      for (w <- 0 until 3) {
        io.b_uart.get(u).wen(w) := false.B
      }
      io.b_uart.get(u).wdata := w_wdata

      for (b <- 0 until p.nDataByte) {
        io.b_uart.get(u).send(b).valid := false.B
        io.b_uart.get(u).send(b).data.get := w_wdata((b + 1) * 8 - 1, b * 8)
        io.b_uart.get(u).rec(b).ready := false.B
      }
    }
  }

  // SPI
  if (p.nSpi > 0) {
    for (s <- 0 until p.nSpi) {
      for (w <- 0 until 3) {
        io.b_spi.get(s).wen(w) := false.B
      }
      io.b_spi.get(s).wdata := w_wdata

      for (b <- 0 until p.nDataByte) {
        io.b_spi.get(s).creq(b).valid := false.B
        io.b_spi.get(s).creq(b).ctrl.get := w_wdata((b + 1) * 8 - 1, b * 8)
        io.b_spi.get(s).dreq(b).valid := false.B
        io.b_spi.get(s).dreq(b).data.get := w_wdata((b + 1) * 8 - 1, b * 8)
        io.b_spi.get(s).read(b).ready := false.B
      }
    }
  }

  // I2C
  if (p.nI2c > 0) {
    for (i <- 0 until p.nI2c) {
      for (w <- 0 until 4) {
        io.b_i2c.get(i).wen(w) := false.B
      }
      io.b_i2c.get(i).wdata := w_wdata

      for (b <- 0 until p.nDataByte) {
        io.b_i2c.get(i).creq(b).valid := false.B
        io.b_i2c.get(i).creq(b).ctrl.get := w_wdata((b + 1) * 8 - 1, b * 8)
        io.b_i2c.get(i).dreq(b).valid := false.B
        io.b_i2c.get(i).dreq(b).data.get := w_wdata((b + 1) * 8 - 1, b * 8)
        io.b_i2c.get(i).read(b).ready := false.B
      }
    }
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
    //            8 BITS
    // ''''''''''''''''''''''''''''''
    // PLIC attribute
    for (ca <- 0 until p.nPlicCause) {
      when (isReqAddr(COMMON.PLIC_ATTR.U + ca.U)) {
        w_rdata := io.b_plic.attr(ca)
      }
    }

    // ''''''''''''''''''''''''''''''
    //            32 BITS
    // ''''''''''''''''''''''''''''''
    // PLIC priority
    for (ca <- 0 until p.nPlicCause) {
      when (isReqAddr(COMMON.PLIC_SIP.U + (ca * 4).U)) {
        w_rdata := io.b_plic.sip(ca)
      }
    }

    // PLIC pending
    for (ca <- 0 until p.nPlicCause32b) {
      when (isReqAddr(COMMON.PLIC_IP.U + (ca * 4).U)) {
        w_rdata := io.b_plic.ip(ca)
      }
    }

    // PLIC enable & config
    for (co <- 0 until p.nPlicContext) {
      for (ca <- 0 until p.nPlicCause32b) {
        when (isReqAddr(COMMON.PLIC_EN.U + (co * 128).U + (ca * 4).U)) {
          w_rdata := io.b_plic.en(co)(ca)
        }
      }
      when (isReqAddr(COMMON.PLIC_THRESHOLD.U + (co * 4096).U)) {
        w_rdata := io.b_plic.cfg(co).threshold
      }
      when (isReqAddr(COMMON.PLIC_CLAIM.U + (co * 4096).U)) {
        w_rdata := io.b_plic.cfg(co).claim
      }
    }

    if (p.useCeps) {
      // L0SI
      if (p.nCepsTrapLvl > 0) {
        for (hw <- 0 until p.nHart32b) {
          when (isReqAddr(CEPS.L0SI.U + (hw * 4).U)) {
            w_rdata := r_lsi(0)(hw)
          }         
        }
      }

      // L1SI
      if (p.nCepsTrapLvl > 1) {
        for (hw <- 0 until p.nHart32b) {
          when (isReqAddr(CEPS.L1SI.U + (hw * 4).U)) {
            w_rdata := r_lsi(1)(hw)
          }         
        }
      }
    } else {
      // MSI 
      for (hw <- 0 until p.nHart32b) {
        when (isReqAddr(PRIV.MSI.U + (hw * 4).U)) {
          w_rdata := r_msi(hw)
        }         
      }  
    }

    // GPIO
    if (p.nGpio > 0) {    
      for (g32 <- 0 until p.nGpio32b) {
        when (isReqAddr(COMMON.GPIOA_ENO.U + (g32 * COMMON.GPIOA_NBYTE).U)) {
          w_rdata := io.b_gpio.get.eno(g32)
        }   
        when (isReqAddr(COMMON.GPIOA_REG.U + (g32 * COMMON.GPIOA_NBYTE).U)) {
          w_rdata := io.b_gpio.get.reg(g32)
        }    
      }
    }

    // PTimer
    for (pt <- 0 until p.nPTimer) {
      when (isReqAddr(COMMON.PTIMER0_STATUS.U + (pt * COMMON.PTIMER0_NBYTE).U)) {
        w_rdata := io.b_ptimer.get(pt).status
      }
      when (isReqAddr(COMMON.PTIMER0_CONFIG.U + (pt * COMMON.PTIMER0_NBYTE).U)) {
        w_rdata := io.b_ptimer.get(pt).config
      }
      when (isReqAddr(COMMON.PTIMER0_CNT.U + (pt * COMMON.PTIMER0_NBYTE).U)) {
        w_rdata := io.b_ptimer.get(pt).cnt(31, 0)
      }
      when (isReqAddr(COMMON.PTIMER0_CNTH.U + (pt * COMMON.PTIMER0_NBYTE).U)) {
        w_rdata := io.b_ptimer.get(pt).cnt(63, 32)
      }
      when (isReqAddr(COMMON.PTIMER0_CMP.U + (pt * COMMON.PTIMER0_NBYTE).U)) {
        w_rdata := io.b_ptimer.get(pt).cmp(31, 0)
      }
      when (isReqAddr(COMMON.PTIMER0_CMPH.U + (pt * COMMON.PTIMER0_NBYTE).U)) {
        w_rdata := io.b_ptimer.get(pt).cmp(63, 32)
      }
    }

    // SPI Flash
    if (p.useSpiFlash) {
      when (isReqAddr(COMMON.SPI_FLASH_STATUS.U)) {
        w_rdata := io.b_spi_flash.get.status        
      } 
      when (isReqAddr(COMMON.SPI_FLASH_CONFIG.U)) {
        w_rdata := io.b_spi_flash.get.config        
      }
      when (isReqAddr(COMMON.SPI_FLASH_NCYCLE.U)) {
        w_rdata := io.b_spi_flash.get.ncycle        
      }
      when (isReqAddr(COMMON.SPI_FLASH_ADDR.U)) {
        w_rdata := io.b_spi_flash.get.addr        
      } 
      when (isReqAddr(COMMON.SPI_FLASH_OFFSET.U)) {
        w_rdata := io.b_spi_flash.get.offset        
      } 
      when (isReqAddr(COMMON.SPI_FLASH_DATA.U)) {
        switch (w_req.ctrl.get.size) {
          is (SIZE.B1.U) {
            io.b_spi_flash.get.read(0).ready := true.B
            w_rdata := io.b_spi_flash.get.read(0).data.get
          }
          is (SIZE.B2.U) {
            for (b <- 0 until 2) {
              io.b_spi_flash.get.read(b).ready := true.B
            }
            w_rdata := Cat(io.b_spi_flash.get.read(1).data.get, io.b_spi_flash.get.read(0).data.get)
          }
          is (SIZE.B4.U) {
            for (b <- 0 until 4) {
              io.b_spi_flash.get.read(b).ready := true.B
            }
            w_rdata := Cat(io.b_spi_flash.get.read(3).data.get, io.b_spi_flash.get.read(2).data.get, io.b_spi_flash.get.read(1).data.get, io.b_spi_flash.get.read(0).data.get)
          }
        }
      }       
    }

    // PS/2 Keyboard
    if (p.usePs2Keyboard) {
      when (isReqAddr(COMMON.PS2_KB_STATUS.U)) {
        w_rdata := io.b_ps2_kb.get.status        
      } 
      when (isReqAddr(COMMON.PS2_KB_CONFIG.U)) {
        w_rdata := io.b_ps2_kb.get.config        
      }
      when (isReqAddr(COMMON.PS2_KB_NCYCLE.U)) {
        w_rdata := io.b_ps2_kb.get.ncycle        
      } 
      when (isReqAddr(COMMON.PS2_KB_DATA.U)) {
        switch (w_req.ctrl.get.size) {
          is (SIZE.B1.U) {
            io.b_ps2_kb.get.rec(0).ready := true.B
            w_rdata := io.b_ps2_kb.get.rec(0).data.get
          }
          is (SIZE.B2.U) {
            for (b <- 0 until 2) {
              io.b_ps2_kb.get.rec(b).ready := true.B
            }
            w_rdata := Cat(io.b_ps2_kb.get.rec(1).data.get, io.b_ps2_kb.get.rec(0).data.get)
          }
          is (SIZE.B4.U) {
            for (b <- 0 until 4) {
              io.b_ps2_kb.get.rec(b).ready := true.B
            }
            w_rdata := Cat(io.b_ps2_kb.get.rec(3).data.get, io.b_ps2_kb.get.rec(2).data.get, io.b_ps2_kb.get.rec(1).data.get, io.b_ps2_kb.get.rec(0).data.get)
          }
        }
      }       
    }

    // UART
    if (p.nUart > 0) {
      for (u <- 0 until p.nUart) {
        when (isReqAddr(COMMON.UART0_STATUS.U + (u * COMMON.UART0_NBYTE).U)) {
          w_rdata := io.b_uart.get(u).status        
        } 
        when (isReqAddr(COMMON.UART0_CONFIG.U + (u * COMMON.UART0_NBYTE).U)) {
          w_rdata := io.b_uart.get(u).config        
        }
        when (isReqAddr(COMMON.UART0_NCYCLE.U + (u * COMMON.UART0_NBYTE).U)) {
          w_rdata := io.b_uart.get(u).ncycle        
        } 
        when (isReqAddr(COMMON.UART0_DATA.U + (u * COMMON.UART0_NBYTE).U)) {
          switch (w_req.ctrl.get.size) {
            is (SIZE.B1.U) {
              io.b_uart.get(u).rec(0).ready := true.B
              w_rdata := io.b_uart.get(u).rec(0).data.get
            }
            is (SIZE.B2.U) {
              for (b <- 0 until 2) {
                io.b_uart.get(u).rec(b).ready := true.B
              }
              w_rdata := Cat(io.b_uart.get(u).rec(1).data.get, io.b_uart.get(u).rec(0).data.get)
            }
            is (SIZE.B4.U) {
              for (b <- 0 until 4) {
                io.b_uart.get(u).rec(b).ready := true.B
              }
              w_rdata := Cat(io.b_uart.get(u).rec(3).data.get, io.b_uart.get(u).rec(2).data.get, io.b_uart.get(u).rec(1).data.get, io.b_uart.get(u).rec(0).data.get)
            }
          }
        } 
      }
    }

    // SPI
    if (p.nSpi > 0) {
      for (s <- 0 until p.nSpi) {
        when (isReqAddr(COMMON.SPI0_STATUS.U + (s * COMMON.SPI0_NBYTE).U)) {
          w_rdata := io.b_spi.get(s).status        
        } 
        when (isReqAddr(COMMON.SPI0_CONFIG.U + (s * COMMON.SPI0_NBYTE).U)) {
          w_rdata := io.b_spi.get(s).config        
        }
        when (isReqAddr(COMMON.SPI0_NCYCLE.U + (s * COMMON.SPI0_NBYTE).U)) {
          w_rdata := io.b_spi.get(s).ncycle        
        } 
        when (isReqAddr(COMMON.SPI0_DATA.U + (s * COMMON.SPI0_NBYTE).U)) {
          switch (w_req.ctrl.get.size) {
            is (SIZE.B1.U) {
              io.b_spi.get(s).read(0).ready := true.B
              w_rdata := io.b_spi.get(s).read(0).data.get
            }
            is (SIZE.B2.U) {
              for (b <- 0 until 2) {
                io.b_spi.get(s).read(b).ready := true.B
              }
              w_rdata := Cat(io.b_spi.get(s).read(1).data.get, io.b_spi.get(s).read(0).data.get)
            }
            is (SIZE.B4.U) {
              for (b <- 0 until 4) {
                io.b_spi.get(s).read(b).ready := true.B
              }
              w_rdata := Cat(io.b_spi.get(s).read(3).data.get, io.b_spi.get(s).read(2).data.get, io.b_spi.get(s).read(1).data.get, io.b_spi.get(s).read(0).data.get)
            }
          }
        } 
      }
    }

    // I2C
    if (p.nI2c > 0) {
      for (i <- 0 until p.nI2c) {
        when (isReqAddr(COMMON.I2C0_STATUS.U + (i * COMMON.I2C0_NBYTE).U)) {
          w_rdata := io.b_i2c.get(i).status        
        } 
        when (isReqAddr(COMMON.I2C0_CONFIG.U + (i * COMMON.I2C0_NBYTE).U)) {
          w_rdata := io.b_i2c.get(i).config        
        }
        when (isReqAddr(COMMON.I2C0_NCYCLE.U + (i * COMMON.I2C0_NBYTE).U)) {
          w_rdata := io.b_i2c.get(i).ncycle        
        } 
        when (isReqAddr(COMMON.I2C0_ADDR.U + (i * COMMON.I2C0_NBYTE).U)) {
          w_rdata := io.b_i2c.get(i).addr        
        } 
        when (isReqAddr(COMMON.I2C0_DATA.U + (i * COMMON.I2C0_NBYTE).U)) {
          switch (w_req.ctrl.get.size) {
            is (SIZE.B1.U) {
              io.b_i2c.get(i).read(0).ready := true.B
              w_rdata := io.b_i2c.get(i).read(0).data.get
            }
            is (SIZE.B2.U) {
              for (b <- 0 until 2) {
                io.b_i2c.get(i).read(b).ready := true.B
              }
              w_rdata := Cat(io.b_i2c.get(i).read(1).data.get, io.b_i2c.get(i).read(0).data.get)
            }
            is (SIZE.B4.U) {
              for (b <- 0 until 4) {
                io.b_i2c.get(i).read(b).ready := true.B
              }
              w_rdata := Cat(io.b_i2c.get(i).read(3).data.get, io.b_i2c.get(i).read(2).data.get, io.b_i2c.get(i).read(1).data.get, io.b_i2c.get(i).read(0).data.get)
            }
          }
        } 
      }
    }

    // ''''''''''''''''''''''''''''''
    //            64 BITS
    // ''''''''''''''''''''''''''''''
    if (p.nDataBit >= 64) {
      when (w_req.ctrl.get.size === SIZE.B8.U) {
        // PLIC pending
        for (ca <- 0 until p.nPlicCause64b) {
          when (isReqAddr(COMMON.PLIC_IP.U + (ca * 8).U)) {
            w_rdata := Cat(io.b_plic.ip(ca * 2 + 1), io.b_plic.ip(ca * 2))
          }
        }    

        // PLIC enable
        for (co <- 0 until p.nPlicContext) {
          for (ca <- 0 until p.nPlicCause64b) {
            when (isReqAddr(COMMON.PLIC_EN.U + (co * 128).U + (ca * 8).U)) {
              w_rdata := Cat(io.b_plic.en(co)(ca * 2 + 1), io.b_plic.en(co)(ca * 2))
            }
          }
        }

        if (p.useCeps) {
          // L0SI 
          if (p.nCepsTrapLvl > 0) {
            for (hd <- 0 until p.nHart64b) {
              when (isReqAddr(CEPS.L0SI.U + (hd * 8).U)) {
                w_rdata := Cat(r_lsi(0)(hd * 2 + 1), r_lsi(0)(hd * 2))
              }         
            }
          }

          // L1SI 
          if (p.nCepsTrapLvl > 1) {
            for (hd <- 0 until p.nHart64b) {
              when (isReqAddr(CEPS.L1SI.U + (hd * 8).U)) {
                w_rdata := Cat(r_lsi(1)(hd * 2 + 1), r_lsi(1)(hd * 2))
              }         
            }
          }
        } else {
          // MSI 
          for (hd <- 0 until p.nHart64b) {
            when (isReqAddr(PRIV.MSI.U + (hd * 8).U)) {
              w_rdata := Cat(r_msi(hd * 2 + 1), r_msi(hd * 2))
            }         
          }
        }

        // PTimer
        for (pt <- 0 until p.nPTimer) {
          when (isReqAddr(COMMON.PTIMER0_CNT.U + (pt * COMMON.PTIMER0_NBYTE).U)) {
            w_rdata := io.b_ptimer.get(pt).cnt
          }
          when (isReqAddr(COMMON.PTIMER0_CMP.U + (pt * COMMON.PTIMER0_NBYTE).U)) {
            w_rdata := io.b_ptimer.get(pt).cmp
          }          
        }

        // SPI Flash
        if (p.usePs2Keyboard) {
          when (isReqAddr(COMMON.SPI_FLASH_DATA.U)) {
            for (b <- 0 until 8) {
              io.b_spi_flash.get.read(b).ready := true.B
            }
            w_rdata := Cat( io.b_spi_flash.get.read(7).data.get, io.b_spi_flash.get.read(6).data.get, io.b_spi_flash.get.read(5).data.get, io.b_spi_flash.get.read(4).data.get,
                            io.b_spi_flash.get.read(3).data.get, io.b_spi_flash.get.read(2).data.get, io.b_spi_flash.get.read(1).data.get, io.b_spi_flash.get.read(0).data.get)
          }          
        }

        // PS/2 Keyboard
        if (p.usePs2Keyboard) {
          when (isReqAddr(COMMON.PS2_KB_DATA.U)) {
            for (b <- 0 until 8) {
              io.b_ps2_kb.get.rec(b).ready := true.B
            }
            w_rdata := Cat( io.b_ps2_kb.get.rec(7).data.get, io.b_ps2_kb.get.rec(6).data.get, io.b_ps2_kb.get.rec(5).data.get, io.b_ps2_kb.get.rec(4).data.get,
                            io.b_ps2_kb.get.rec(3).data.get, io.b_ps2_kb.get.rec(2).data.get, io.b_ps2_kb.get.rec(1).data.get, io.b_ps2_kb.get.rec(0).data.get)
          }          
        }

        // UART
        if (p.nUart > 0) {
          for (u <- 0 until p.nUart) {
            when (isReqAddr(COMMON.UART0_DATA.U + (u * COMMON.UART0_NBYTE).U)) {
              for (b <- 0 until 8) {
                io.b_uart.get(u).rec(b).ready := true.B
              }
              w_rdata := Cat( io.b_uart.get(u).rec(7).data.get, io.b_uart.get(u).rec(6).data.get, io.b_uart.get(u).rec(5).data.get, io.b_uart.get(u).rec(4).data.get,
                              io.b_uart.get(u).rec(3).data.get, io.b_uart.get(u).rec(2).data.get, io.b_uart.get(u).rec(1).data.get, io.b_uart.get(u).rec(0).data.get)
            }
          }
        }

        // SPI
        if (p.nSpi > 0) {
          for (s <- 0 until p.nSpi) {
            when (isReqAddr(COMMON.SPI0_DATA.U + (s * COMMON.SPI0_NBYTE).U)) {
              for (b <- 0 until 8) {
                io.b_spi.get(s).read(b).ready := true.B
              }
              w_rdata := Cat( io.b_spi.get(s).read(7).data.get, io.b_spi.get(s).read(6).data.get, io.b_spi.get(s).read(5).data.get, io.b_spi.get(s).read(4).data.get,
                              io.b_spi.get(s).read(3).data.get, io.b_spi.get(s).read(2).data.get, io.b_spi.get(s).read(1).data.get, io.b_spi.get(s).read(0).data.get)
            }
          }
        }

        // I2C
        if (p.nI2c > 0) {
          for (i <- 0 until p.nI2c) {
            when (isReqAddr(COMMON.I2C0_DATA.U + (i * COMMON.I2C0_NBYTE).U)) {
              for (b <- 0 until 8) {
                io.b_i2c.get(i).read(b).ready := true.B
              }
              w_rdata := Cat( io.b_i2c.get(i).read(7).data.get, io.b_i2c.get(i).read(6).data.get, io.b_i2c.get(i).read(5).data.get, io.b_i2c.get(i).read(4).data.get,
                              io.b_i2c.get(i).read(3).data.get, io.b_i2c.get(i).read(2).data.get, io.b_i2c.get(i).read(1).data.get, io.b_i2c.get(i).read(0).data.get)
            }
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
  // Operation
  if (!p.readOnly) {
    when (w_ack.valid & w_ack.ctrl.get.wo & ~w_ack_pwait) {
      // ''''''''''''''''''''''''''''''
      //            8 BITS
      // ''''''''''''''''''''''''''''''
      // PLIC attribute
      for (ca <- 0 until p.nPlicCause) {
        io.b_plic.wattr(ca) := isAckAddr(COMMON.PLIC_ATTR.U + ca.U)
      }
  
      // ''''''''''''''''''''''''''''''
      //            32 BITS
      // ''''''''''''''''''''''''''''''
      // PLIC priority
      for (ca <- 0 until p.nPlicCause) {
        io.b_plic.wsip(ca) := isAckAddr(COMMON.PLIC_SIP.U + (ca * 4).U)
      }
  
      // PLIC pending
      for (ca <- 0 until p.nPlicCause32b) {
        io.b_plic.wip(ca) := isAckAddr(COMMON.PLIC_IP.U + (ca * 4).U)
      }
  
      // PLIC enable & config
      for (co <- 0 until p.nPlicContext) {
        for (ca <- 0 until p.nPlicCause32b) {
          io.b_plic.wen(co)(ca) := isAckAddr(COMMON.PLIC_EN.U + (co * 128).U + (ca * 4).U)
        }
        io.b_plic.wcfg(co)(0) := isAckAddr(COMMON.PLIC_THRESHOLD.U + (co * 4096).U)
        io.b_plic.wcfg(co)(1) := isAckAddr(COMMON.PLIC_CLAIM.U + (co * 4096).U)
      }

      if (p.useCeps) {
        // L0SI 
        if (p.nCepsTrapLvl > 0) {
          for (hw <- 0 until p.nHart32b) {
            when (isAckAddr(CEPS.L0SI.U + (hw * 4).U)) {
              r_lsi(0)(hw) := w_wdata(31, 0)
            }         
          }
        }

        // L1SI 
        if (p.nCepsTrapLvl > 0) {
          for (hw <- 0 until p.nHart32b) {
            when (isAckAddr(CEPS.L1SI.U + (hw * 4).U)) {
              r_lsi(1)(hw) := w_wdata(31, 0)
            }         
          }
        }
      } else {
        // MSI 
        for (hw <- 0 until p.nHart32b) {
          when (isAckAddr(PRIV.MSI.U + (hw * 4).U)) {
            r_msi(hw) := w_wdata(31, 0)
          }         
        }  
      }
  
      // GPIO
      if (p.nGpio > 0) {
        for (g32 <- 0 until p.nGpio32b) {
          io.b_gpio.get.wen(g32 * 2) := isAckAddr(COMMON.GPIOA_ENO.U + (g32 * COMMON.GPIOA_NBYTE).U)
          io.b_gpio.get.wen(g32 * 2 + 1) := isAckAddr(COMMON.GPIOA_REG.U + (g32 * COMMON.GPIOA_NBYTE).U)

          when (isAckAddr(COMMON.GPIOA_SET.U + (g32 * COMMON.GPIOA_NBYTE).U)) {
            io.b_gpio.get.wen(g32 * 2 + 1) := true.B
            io.b_gpio.get.wdata := io.b_gpio.get.reg(g32) | w_wdata
          }

          when (isAckAddr(COMMON.GPIOA_RST.U + (g32 * COMMON.GPIOA_NBYTE).U)) {
            io.b_gpio.get.wen(g32 * 2 + 1) := true.B
            io.b_gpio.get.wdata := io.b_gpio.get.reg(g32) & ~w_wdata
          }
        }
      }
  
      // PTimer
      for (pt <- 0 until p.nPTimer) {
        io.b_ptimer.get(pt).wen(1) := (isAckAddr(COMMON.PTIMER0_CONFIG.U + (pt * COMMON.PTIMER0_NBYTE).U))
        io.b_ptimer.get(pt).wen(2) := (isAckAddr(COMMON.PTIMER0_CNT.U + (pt * COMMON.PTIMER0_NBYTE).U))
        io.b_ptimer.get(pt).wen(3) := (isAckAddr(COMMON.PTIMER0_CNTH.U + (pt * COMMON.PTIMER0_NBYTE).U))
        io.b_ptimer.get(pt).wen(4) := (isAckAddr(COMMON.PTIMER0_CMP.U + (pt * COMMON.PTIMER0_NBYTE).U))
        io.b_ptimer.get(pt).wen(5) := (isAckAddr(COMMON.PTIMER0_CMPH.U + (pt * COMMON.PTIMER0_NBYTE).U))
      }
  
      // SPI Flash
      if (p.useSpiFlash) {
        io.b_spi_flash.get.wen(1) := isAckAddr(COMMON.SPI_FLASH_CONFIG.U)
        io.b_spi_flash.get.wen(2) := isAckAddr(COMMON.SPI_FLASH_NCYCLE.U)
        io.b_spi_flash.get.wen(3) := isAckAddr(COMMON.SPI_FLASH_ADDR.U)
        io.b_spi_flash.get.wen(4) := isAckAddr(COMMON.SPI_FLASH_OFFSET.U)
        switch (w_ack.ctrl.get.size) {
          is (SIZE.B1.U) {
            io.b_spi_flash.get.creq(0).valid := isAckAddr(COMMON.SPI_FLASH_CMD.U)
            io.b_spi_flash.get.dreq(0).valid := isAckAddr(COMMON.SPI_FLASH_DATA.U)
          }
          is (SIZE.B2.U) {
            for (b <- 0 until 2) {
              io.b_spi_flash.get.creq(b).valid := isAckAddr(COMMON.SPI_FLASH_CMD.U)
              io.b_spi_flash.get.dreq(b).valid := isAckAddr(COMMON.SPI_FLASH_DATA.U)
            }
          }
          is (SIZE.B4.U) {
            for (b <- 0 until 4) {
              io.b_spi_flash.get.creq(b).valid := isAckAddr(COMMON.SPI_FLASH_CMD.U)
              io.b_spi_flash.get.dreq(b).valid := isAckAddr(COMMON.SPI_FLASH_DATA.U)
            }
          }
        }               
      }
  
      // PS/2 Keyboard
      if (p.usePs2Keyboard) {
        io.b_ps2_kb.get.wen(1) := isAckAddr(COMMON.PS2_KB_CONFIG.U)
        io.b_ps2_kb.get.wen(2) := isAckAddr(COMMON.PS2_KB_NCYCLE.U)
        when (isAckAddr(COMMON.PS2_KB_DATA.U)) {
          switch (w_ack.ctrl.get.size) {
            is (SIZE.B1.U) {
              io.b_ps2_kb.get.send(0).valid := true.B
            }
            is (SIZE.B2.U) {
              for (b <- 0 until 2) {
                io.b_ps2_kb.get.send(b).valid := true.B
              }
            }
            is (SIZE.B4.U) {
              for (b <- 0 until 4) {
                io.b_ps2_kb.get.send(b).valid := true.B
              }
            }
          }
        }        
      }
  
      // UART
      if (p.nUart > 0) {
        for (u <- 0 until p.nUart) {
          io.b_uart.get(u).wen(1) := isAckAddr(COMMON.UART0_CONFIG.U + (u * COMMON.UART0_NBYTE).U)
          io.b_uart.get(u).wen(2) := isAckAddr(COMMON.UART0_NCYCLE.U + (u * COMMON.UART0_NBYTE).U)
          when (isAckAddr(COMMON.UART0_DATA.U + (u * COMMON.UART0_NBYTE).U)) {
            switch (w_ack.ctrl.get.size) {
              is (SIZE.B1.U) {
                io.b_uart.get(u).send(0).valid := true.B
              }
              is (SIZE.B2.U) {
                for (b <- 0 until 2) {
                  io.b_uart.get(u).send(b).valid := true.B
                }
              }
              is (SIZE.B4.U) {
                for (b <- 0 until 4) {
                  io.b_uart.get(u).send(b).valid := true.B
                }
              }
            }
          }
        }
      }
  
      // SPI
      if (p.nSpi > 0) {
        for (s <- 0 until p.nSpi) {
          io.b_spi.get(s).wen(1) := isAckAddr(COMMON.SPI0_CONFIG.U + (s * COMMON.SPI0_NBYTE).U)
          io.b_spi.get(s).wen(2) := isAckAddr(COMMON.SPI0_NCYCLE.U + (s * COMMON.SPI0_NBYTE).U)
          switch (w_ack.ctrl.get.size) {
            is (SIZE.B1.U) {
              io.b_spi.get(s).creq(0).valid := isAckAddr(COMMON.SPI0_CMD.U + (s * COMMON.SPI0_NBYTE).U)
              io.b_spi.get(s).dreq(0).valid := isAckAddr(COMMON.SPI0_DATA.U + (s * COMMON.SPI0_NBYTE).U)
            }
            is (SIZE.B2.U) {
              for (b <- 0 until 2) {
                io.b_spi.get(s).creq(b).valid := isAckAddr(COMMON.SPI0_CMD.U + (s * COMMON.SPI0_NBYTE).U)
                io.b_spi.get(s).dreq(b).valid := isAckAddr(COMMON.SPI0_DATA.U + (s * COMMON.SPI0_NBYTE).U)
              }
            }
            is (SIZE.B4.U) {
              for (b <- 0 until 4) {
                io.b_spi.get(s).creq(b).valid := isAckAddr(COMMON.SPI0_CMD.U + (s * COMMON.SPI0_NBYTE).U)
                io.b_spi.get(s).dreq(b).valid := isAckAddr(COMMON.SPI0_DATA.U + (s * COMMON.SPI0_NBYTE).U)
              }
            }
          }          
        }
      }
  
      // I2C
      if (p.nI2c > 0) {
        for (i <- 0 until p.nI2c) {
          io.b_i2c.get(i).wen(1) := isAckAddr(COMMON.I2C0_CONFIG.U + (i * COMMON.I2C0_NBYTE).U)
          io.b_i2c.get(i).wen(2) := isAckAddr(COMMON.I2C0_NCYCLE.U + (i * COMMON.I2C0_NBYTE).U)
          io.b_i2c.get(i).wen(3) := isAckAddr(COMMON.I2C0_ADDR.U + (i * COMMON.I2C0_NBYTE).U)
          switch (w_ack.ctrl.get.size) {
            is (SIZE.B1.U) {
              io.b_i2c.get(i).creq(0).valid := isAckAddr(COMMON.I2C0_CMD.U + (i * COMMON.I2C0_NBYTE).U)
              io.b_i2c.get(i).dreq(0).valid := isAckAddr(COMMON.I2C0_DATA.U + (i * COMMON.I2C0_NBYTE).U)
            }
            is (SIZE.B2.U) {
              for (b <- 0 until 2) {
                io.b_i2c.get(i).creq(b).valid := isAckAddr(COMMON.I2C0_CMD.U + (i * COMMON.I2C0_NBYTE).U)
                io.b_i2c.get(i).dreq(b).valid := isAckAddr(COMMON.I2C0_DATA.U + (i * COMMON.I2C0_NBYTE).U)
              }
            }
            is (SIZE.B4.U) {
              for (b <- 0 until 4) {
                io.b_i2c.get(i).creq(b).valid := isAckAddr(COMMON.I2C0_CMD.U + (i * COMMON.I2C0_NBYTE).U)
                io.b_i2c.get(i).dreq(b).valid := isAckAddr(COMMON.I2C0_DATA.U + (i * COMMON.I2C0_NBYTE).U)
              }
            }
          }          
        }
      }
  
      // ''''''''''''''''''''''''''''''
      //            64 BITS
      // ''''''''''''''''''''''''''''''
      if (p.nDataBit >= 64) {
        when (w_ack.ctrl.get.size === SIZE.B8.U) {
          // PLIC pending
          for (ca <- 0 until p.nPlicCause64b) {
            io.b_plic.wip(ca * 2 + 1) := isAckAddr(COMMON.PLIC_IP.U + (ca * 8).U)
          }
  
          // PLIC enable
          for (co <- 0 until p.nPlicContext) {
            for (ca <- 0 until p.nPlicCause64b) {
              io.b_plic.wen(co)(ca * 2 + 1) := isAckAddr(COMMON.PLIC_EN.U + (co * 128).U + (ca * 8).U)
            }
          }

          if (p.useCeps) {
            // L0SI 
            if (p.nCepsTrapLvl > 0) {
              for (hd <- 0 until p.nHart64b) {
                when (isAckAddr(CEPS.L0SI.U + (hd * 8).U)) {
                  r_lsi(0)(hd * 2 + 1) := w_wdata(63, 32)
                }         
              }
            }

            // L1SI 
            if (p.nCepsTrapLvl > 1) {
              for (hd <- 0 until p.nHart64b) {
                when (isAckAddr(CEPS.L1SI.U + (hd * 8).U)) {
                  r_lsi(1)(hd * 2 + 1) := w_wdata(63, 32)
                }         
              }
            }          
          } else {
            // MSI 
            for (hd <- 0 until p.nHart64b) {
              when (isAckAddr(PRIV.MSI.U + (hd * 8).U)) {
                r_msi(hd * 2 + 1) := w_wdata(63, 32)
              }         
            }  
          }
          
          // PTimer
          for (pt <- 0 until p.nPTimer) {
            io.b_ptimer.get(pt).wen(2) := (isAckAddr(COMMON.PTIMER0_CNT.U + (pt * COMMON.PTIMER0_NBYTE).U))
            io.b_ptimer.get(pt).wen(4) := (isAckAddr(COMMON.PTIMER0_CMP.U + (pt * COMMON.PTIMER0_NBYTE).U))
          }
  
          // SPI Flash
          if (p.useSpiFlash) {
            for (b <- 0 until 8) {
              io.b_spi_flash.get.creq(b).valid := isAckAddr(COMMON.SPI_FLASH_CMD.U)
              io.b_spi_flash.get.dreq(b).valid := isAckAddr(COMMON.SPI_FLASH_DATA.U)
            }                        
          }
  
          // PS/2 Keyboard
          if (p.usePs2Keyboard) {
            when (isAckAddr(COMMON.PS2_KB_DATA.U)) {
              for (b <- 0 until 8) {
                io.b_ps2_kb.get.send(b).valid := true.B
              }
            }            
          }
  
          // UART
          if (p.nUart > 0) {
            for (u <- 0 until p.nUart) {
              when (isAckAddr(COMMON.UART0_DATA.U + (u * COMMON.UART0_NBYTE).U)) {
                for (b <- 0 until 8) {
                  io.b_uart.get(u).send(b).valid := true.B
                }
              }
            }
          }
  
          // SPI
          if (p.nSpi > 0) {
            for (s <- 0 until p.nSpi) {
              for (b <- 0 until 8) {
                io.b_spi.get(s).creq(b).valid := isAckAddr(COMMON.SPI0_CMD.U + (s * COMMON.SPI0_NBYTE).U)
                io.b_spi.get(s).dreq(b).valid := isAckAddr(COMMON.SPI0_DATA.U + (s * COMMON.SPI0_NBYTE).U)
              }              
            }
          }
  
          // I2C
          if (p.nI2c > 0) {
            for (i <- 0 until p.nI2c) {
              for (b <- 0 until 8) {
                io.b_i2c.get(i).creq(b).valid := isAckAddr(COMMON.I2C0_CMD.U + (i * COMMON.I2C0_NBYTE).U)
                io.b_i2c.get(i).dreq(b).valid := isAckAddr(COMMON.I2C0_DATA.U + (i * COMMON.I2C0_NBYTE).U)
              }              
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
  // Interrupts
  if (p.useCeps) {
    for (tl <- 0 until p.nCepsTrapLvl) {
      io.o_irq_lsi.get(tl) := (r_lsi(tl).asUInt).asBools
    }
  } else {
    io.o_irq_msi.get := (r_msi.asUInt).asBools
    io.o_irq_ssi.get := (r_ssi.asUInt).asBools 
  }

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


