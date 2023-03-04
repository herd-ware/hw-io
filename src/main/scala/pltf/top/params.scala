/*
 * File: params.scala
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-03 03:57:23 pm
 * Modified By: Mathieu Escouteloup
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.pltf

import chisel3._
import chisel3.experimental.IO
import chisel3.util._

import herd.common.mem.mb4s._
import herd.io.pltf.regmem._
import herd.io.periph.gpio._
import herd.io.periph.spi._
import herd.io.periph.spi_flash._
import herd.io.periph.uart._
import herd.io.periph.timer._
import herd.io.periph.ps2._
import herd.io.periph.i2c._


trait IOPltfParams extends RegMemParams {
  def pPort: Array[Mb4sParams]

  def debug: Boolean
  def nAddrBit: Int
  def nAddrBase: String

  def nChampTrapLvl: Int

  def useReqReg: Boolean
  def nPlicPrio: Int
  def nGpio: Int
  def nUart: Int
  def nUartFifoDepth: Int
  def nPTimer: Int  
  def useSpiFlash: Boolean 
  def usePs2Keyboard: Boolean
  def nSpi: Int 
  def nSpiSlave: Array[Int]
  def nSpiFifoDepth: Int
  def nI2c: Int 
  def nI2cFifoDepth: Int

  def pGpio: GpioParams = new GpioConfig (
    debug = debug,
    nGpio = nGpio
  )

  def pPTimer: TimerParams = new TimerConfig (
    debug = debug
  )

  def pSpiFlash: SpiFlashParams = SpiFlashConfig (
    debug = debug,
    nDataByte = nDataByte,
    useRegMem = true,
    useDma = false,
    nBufferDepth = nDataByte * 2
  )

  def pPs2Keyboard: Ps2Params = new Ps2Config (
    debug = debug,
    nDataByte = nDataByte,
    useRegMem = true,
    nBufferDepth = nDataByte * 2
  )
  
  def pUart: UartParams = new UartConfig (
    debug = debug,
    nDataByte = nDataByte,
    useRegMem = true,
    nBufferDepth = nUartFifoDepth
  )

  def pSpi: Array[SpiParams] = {
    var ps: Array[SpiParams] = Array()
    for (n <- 0 until nSpi) {
      ps = ps :+ (new SpiConfig (
        debug = debug,
        nDataByte = nDataByte,
        nSlave = if (nSpiSlave.size > n) {
          nSpiSlave(n)
        } else {
          1
        },
        useRegMem = true,
        nBufferDepth = nSpiFifoDepth
      ))
    }
    return ps
  }

  def pI2c: I2cParams = new I2cConfig (
    debug = debug,
    nDataByte = nDataByte,
    useRegMem = true,
    nBufferDepth = nI2cFifoDepth
  )
}

case class IOPltfConfig (
  pPort: Array[Mb4sParams],

  debug: Boolean,
  nAddrBit: Int,
  nAddrBase: String,

  nChampTrapLvl: Int,

  useReqReg: Boolean,
  nPlicPrio: Int,
  nGpio: Int,
  nUart: Int,
  nUartFifoDepth: Int,
  nPTimer: Int,  
  useSpiFlash: Boolean,
  usePs2Keyboard: Boolean,
  nSpi: Int,
  nSpiSlave: Array[Int],
  nSpiFifoDepth: Int,
  nI2c: Int,
  nI2cFifoDepth: Int
) extends IOPltfParams
