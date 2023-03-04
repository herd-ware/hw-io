/*
 * File: configs.scala
 * Created Date: 2023-02-25 09:48:16 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-02 09:34:17 pm
 * Modified By: Mathieu Escouteloup
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.io.core.hpm

import chisel3._
import chisel3.experimental.IO
import chisel3.util._

object HpmConfigBase extends HpmConfig (
  debug = true,
  nHart = 1, 

  isHpmAct = Array("ALL"),
  hasHpmMap = Array()
)