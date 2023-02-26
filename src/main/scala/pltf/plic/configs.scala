/*
 * File: configs.scala                                                         *
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:10:54 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.pltf.plic

import chisel3._
import chisel3.experimental.IO
import chisel3.util._


object PlicConfigBase extends PlicConfig (
  nDataBit = 32,

  nPlicContext = 1,
  nPlicCauseUse = CAUSE.USE(
    nUart = 1,
    nPTimer = 2
  ),
  nPlicPrio = 32
)