/*
 * File: configs.scala                                                         *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:11:25 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
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


object IOPltfConfigBase extends IOPltfConfig (
  pPort = Array(new Mb4sConfig (
    debug = true,
    readOnly = false,
    nHart = 2,
    nAddrBit = 32,
    useAmo = false,
    nDataByte = 4,
    useDome = true,
    nDome = 2,
    multiDome = false
  )),

  debug = true,
  nAddrBit = 32,
  nAddrBase = "00000000",

  nCepsTrapLvl = 2,

  useReqReg = false,
  nPlicPrio = 8,
  nGpio = 128,
  nUart = 1,
  nUartFifoDepth = 8,
  nPTimer = 4,  
  useSpiFlash = true,
  usePs2Keyboard = true,
  nSpi = 1,
  nSpiSlave = Array(1),
  nSpiFifoDepth = 8,
  nI2c = 1,
  nI2cFifoDepth = 8
)
