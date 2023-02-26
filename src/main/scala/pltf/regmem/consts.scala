/*
 * File: consts.scala                                                          *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:11:14 pm                                       *
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


// ******************************
//           CONSTANTS
// ******************************
object CST {
  def MAXGPIO     = 256
  def MAXPTIMER   = 4
  def MAXUART     = 1
  def MAXSPI      = 1
}

// ******************************
//        REGISTER ADDRESSES
// ******************************
object COMMON {
  def NBIT = 28

  // PLIC
  def PLIC_SIP              = "h0000000"
  def PLIC_IP               = "h0001000"
  def PLIC_ATTR             = "h0001400"
  def PLIC_EN               = "h0002000"
  def PLIC_THRESHOLD        = "h0200000"
  def PLIC_CLAIM            = "h0200004"
      
  // GPIO (First A)   
  def GPIOA_MAX             = 8 
  def GPIOA_NBYTE           = 0x10
  def GPIOA_ENO             = "h4004000"
  def GPIOA_REG             = "h4004004"
  def GPIOA_SET             = "h4004008"
  def GPIOA_RST             = "h400400c"

  // PTimer (First 0)       
  def PTIMER0_MAX           = 4  
  def PTIMER0_NBYTE         = 0x20
  def PTIMER0_STATUS        = "h4004400"
  def PTIMER0_CONFIG        = "h4004404"
  def PTIMER0_CNT           = "h4004410"
  def PTIMER0_CNTH          = "h4004414"
  def PTIMER0_CMP           = "h4004418"
  def PTIMER0_CMPH          = "h400441c"

  // DMA      
  def DMA_NBYTE             = 0x100
  def DMA_STATUS            = "h4004600"
  def DMA_HART              = "h4004604"
  def DMA_CONFIG            = "h4004608"
  def DMA_MADDR             = "h4004610"
  def DMA_MADDRH            = "h4004614"
  def DMA_SADDR             = "h4004618"
  def DMA_SADDRH            = "h400461c"
  def DMA_OFFSET            = "h4004620"
  def DMA_OFFSETH           = "h4004624"

  // SPI Flash   
  def SPI_NBYTE             = 0x30
  def SPI_FLASH_STATUS      = "h4004700"
  def SPI_FLASH_CONFIG      = "h4004704"
  def SPI_FLASH_NCYCLE      = "h4004708"
  def SPI_FLASH_ADDR        = "h400470c"
  def SPI_FLASH_OFFSET      = "h4004710"
  def SPI_FLASH_CMD         = "h4004720"
  def SPI_FLASH_DATA        = "h4004728"

  // PS/2 Keyboard     
  def PS2_KB_NBYTE          = 0x20
  def PS2_KB_STATUS         = "h4004740"
  def PS2_KB_CONFIG         = "h4004744"
  def PS2_KB_NCYCLE         = "h4004748"
  def PS2_KB_DATA           = "h4004750"

  // UART (First 0)     
  def UART0_MAX             = 1
  def UART0_NBYTE           = 0x20
  def UART0_STATUS          = "h4004800"
  def UART0_CONFIG          = "h4004804"
  def UART0_NCYCLE          = "h4004808"
  def UART0_DATA            = "h4004810"

  // SPI (First 0)     
  def SPI0_MAX              = 1
  def SPI0_NBYTE            = 0x20
  def SPI0_STATUS           = "h4004900"
  def SPI0_CONFIG           = "h4004904"
  def SPI0_NCYCLE           = "h4004908"
  def SPI0_CMD              = "h4004910"
  def SPI0_DATA             = "h4004918"

  // I2C (First 0)     
  def I2C0_MAX              = 1
  def I2C0_NBYTE            = 0x20
  def I2C0_STATUS           = "h4004a00"
  def I2C0_CONFIG           = "h4004a04"
  def I2C0_NCYCLE           = "h4004a08"
  def I2C0_ADDR             = "h4004a0c"
  def I2C0_CMD              = "h4004a10"
  def I2C0_DATA             = "h4004a18"
}

object PRIV {
  // Software interrupts
  def MSI             = "h4000000"
  def SSI             = "h4001000"
}

object CEPS {
  // Software interrupts
  def L0SI            = "h4000000"
  def L1SI            = "h4001000"
}