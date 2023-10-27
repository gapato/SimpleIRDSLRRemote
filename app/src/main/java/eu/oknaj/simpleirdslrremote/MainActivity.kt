/*
Simple IR DSLR Remote - Use your smartphone's IR blaster to replace the IR remote for you camera.

This work is licensed under the Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License.
 view a copy of this license, visit http://creativecommons.org/licenses/by-nc-sa/3.0/ or send a letter to
 Creative Commons, 171 Second Street, Suite 300, San Francisco, California, 94105, USA.

Copyright (C) 2013  Sebastian Setz for the IR frequencies and patterns,
                    see http://sebastian.setz.name/arduino/my-libraries/multiCameraIrControl
Copyright (C) 2023  Gapato
 */

package eu.oknaj.simpleirdslrremote

import android.content.Context
import android.os.Bundle
import android.hardware.ConsumerIrManager
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults.buttonColors
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import eu.oknaj.simpleirdslrremote.ui.theme.SimpleIRDSLRRemoteTheme
import java.lang.Exception
import java.lang.Float.max
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.timerTask
import kotlin.math.ceil

var mCameras: Array<String> = arrayOf()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mCameras = arrayOf(
            "Nikon",
            "Canon" + getString(R.string.untested_suffix),
            "Minolta" + getString(R.string.untested_suffix),
            "Olympus" + getString(R.string.untested_suffix),
            "Pentax" + getString(R.string.untested_suffix),
            "Sony" + getString(R.string.untested_suffix)
        )

        setContent {
            SimpleIRDSLRRemoteTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainColumn(handleExit = { this.finishAndRemoveTask() })
                }
            }
        }
    }
}

@Composable
fun MainColumn(handleExit: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    var hasIr = pm.hasSystemFeature(PackageManager.FEATURE_CONSUMER_IR)
    lateinit var irManager: ConsumerIrManager
    if (hasIr) {
        try {
            irManager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as ConsumerIrManager
            hasIr = irManager.hasIrEmitter()
        } catch (e: AssertionError) {
            hasIr = false
        }
    }

    var camera by remember { mutableStateOf(mCameras[0]) }
    var interval by remember { mutableStateOf("1") }
    var nShots by remember { mutableStateOf("1") }
    var remainingShots by remember { mutableIntStateOf(0) }
    var remainingSeconds by remember { mutableFloatStateOf(0F) }
    var busy by remember { mutableStateOf(false) }

    var intervalTask by remember { mutableStateOf(timerTask{})}
    var tickTask by remember { mutableStateOf(timerTask{})}

    val shutterTimer = Timer()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp, 10.dp)
    ) {
        Greeting(hasIr, busy, remainingShots)
        if (hasIr) {
            CameraSelector(camera, onChangeCamera = { camera = it }, !busy)
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(0.dp, 10.dp)
            ) {
                IntegerField(
                    label = stringResource(id = R.string.interval_label),
                    shortLabel = stringResource(id = R.string.interval_label_short),
                    value = interval,
                    enabled = !busy,
                    onChangeValue = { interval = it },
                    modifier = Modifier
                        .padding(0.dp, 0.dp, 5.dp, 0.dp)
                        .fillMaxWidth(0.5f)
                )
                IntegerField(
                    label = stringResource(id = R.string.n_shots_label),
                    value = nShots,
                    enabled =!busy,
                    onChangeValue = { nShots = it },
                    modifier = Modifier.padding(5.dp, 0.dp, 0.dp, 0.dp)
                )
            }
            if (busy) {
                CancelButton(
                    onClick = {
                        Log.d("CancelButton", "canceling")
                        intervalTask.cancel()
                        tickTask.cancel()
                        remainingShots = 0
                        busy = false
                    },
                    remainingShots = remainingShots,
                    remainingSeconds = remainingSeconds
                )
            } else {
                ShutterButton(
                    nShots = nShots,
                    onClick = {
                        var intervalInt = 1
                        remainingShots = 1

                        try {
                            intervalInt = interval.toInt()
                            remainingShots = nShots.toInt()
                        } catch (_: Exception) {
                        }
                        remainingShots = nShots.toInt()
                        shutterAction(irManager,
                            camera,
                            remainingShots,
                            intervalInt,
                            afterShutter = { remainingShots-- },
                            timer = shutterTimer,
                            setIntervalTask = { it: TimerTask -> intervalTask = it },
                            setTickTask = { it: TimerTask -> tickTask = it },
                            resetTick = { remainingSeconds = intervalInt.toFloat() },
                            tick = { remainingSeconds = max(0F, remainingSeconds - 0.1F) },
                            busyOn = { busy = true },
                            busyOff = { busy = false })
                    },
                )
            }
        }
        Spacer(Modifier.height(40.dp))
        ExitButton(handleExitClick = handleExit)
        VersionFooter()
    }
}

@Composable
fun Greeting(
    hasIr: Boolean,
    shuttering: Boolean,
    remainingShots: Int,
    modifier: Modifier = Modifier
) {
    var headingText = stringResource(id = R.string.no_ir_found)

    if (hasIr) {
        headingText = stringResource(id = R.string.ir_found)
    }

    if (shuttering && remainingShots > 0) {
        headingText = stringResource(id = R.string.intervalometer_running)
    }

    Text(
        text = headingText,
        modifier = modifier
    )

}

@Composable
fun VersionFooter(){
    Text(text = "v" + BuildConfig.VERSION_NAME, fontSize=10.sp, color=Color.Gray,
        textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth().padding(10.dp, 0.dp))
}

@Composable
fun IntegerField(
    label: String,
    value: String,
    enabled: Boolean,
    onChangeValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    shortLabel: String = "",
    ) {
    OutlinedTextField(
        value = value,
        enabled = enabled,
        onValueChange = { newValue: String -> onChangeValue(newValue.filter { it.isDigit() }) },
        label = { Text(label) },
        placeholder = { Text(if (shortLabel == "") label else shortLabel)},
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
fun ShutterButton(onClick: () -> Unit, nShots: String) {

    var nShotsInt = 0
    try {
        nShotsInt = nShots.toInt()
    } catch (_: Exception) {}

    var label = stringResource(id = R.string.shutter_label)

    if (nShotsInt > 1) {
        label = stringResource(id = R.string.intervalometer_label)
    }

    val textStyle = TextStyle(
        textAlign = TextAlign.Center,
        lineHeight = 45.sp
    )

    Button(
        enabled = nShotsInt > 0,
        onClick = { onClick() },
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.8F),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(text = label,
            style = textStyle,
            fontSize = 40.sp)
    }
}

@Composable
fun CancelButton(onClick: () -> Unit, remainingShots: Int, remainingSeconds: Float) {

    val colors = buttonColors(containerColor = Color.Red)
    val remainingSecondsInt = ceil(remainingSeconds).toInt()

    Button(
        onClick = onClick,
        colors = colors,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.8F),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = stringResource(id = R.string.cancel_label), fontSize = 40.sp)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = pluralStringResource(
                    R.plurals.timelapse_shots_left,
                    remainingShots,
                    remainingShots
                ) +
                        ", " + pluralStringResource(
                    R.plurals.timelapse_seconds_left,
                    remainingSecondsInt,
                    remainingSecondsInt
                    )
            )
        }
    }
}

// move this up to MainColumn
fun shutterAction(
    irManager: ConsumerIrManager, camera: String, totalShots: Int, interval: Int,
    afterShutter: () -> Unit, timer: Timer, busyOn: () -> Unit, busyOff: () -> Unit,
    setIntervalTask: (TimerTask) -> Unit, resetTick: () -> Unit, tick: () -> Unit,
    setTickTask: (TimerTask) -> Unit
) {
    val idx = mCameras.indexOf(camera)

    val shutterFunctions = arrayOf(
        ::shutterNikon,
        ::shutterCanon,
        ::shutterMinolta,
        ::shutterOlympus,
        ::shutterPentax,
        ::shutterSony
    )

    val period = 1000 * interval.toLong()

    if (idx >= 0) {
        if (totalShots > 1 || interval > 1) {
            busyOn()
            var nShots = 0

            var tickTask = timerTask { tick() }

            val intervalTask = timerTask {

                tickTask.cancel()
                resetTick()
                tickTask = timerTask { tick() }
                timer.scheduleAtFixedRate(tickTask, 0, 100)
                setTickTask(tickTask)

                shutterFunctions[idx](irManager)
                afterShutter()
                Log.d("shutterAction", "nShots: $nShots")
                Log.d("shutterAction", "period: $period")
                nShots += 1

                if (nShots == totalShots) {
                    busyOff()
                    Log.d("shutterAction", "reached $nShots/$totalShots")
                    timer.cancel()
                    tickTask.cancel()
                }
            }
            resetTick()
            timer.scheduleAtFixedRate(intervalTask, period, period)
            timer.scheduleAtFixedRate(tickTask, 0, 100)
            setIntervalTask(intervalTask)
            setTickTask(tickTask)
        } else {
            busyOn()
            shutterFunctions[idx](irManager)
            busyOff()
        }

    }
}

@Composable
fun ExitButton(handleExitClick: () -> Unit) {
    TextButton(
        onClick = { handleExitClick() },
    ) {
        Text(text = stringResource(id = R.string.quit_label))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraSelector(camera: String, onChangeCamera: (String) -> Unit, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    var mTextFieldSize by remember { mutableStateOf(Size.Zero) }

// We want to react on tap/press on TextField to show menu
    ExposedDropdownMenuBox(
        expanded = expanded,
        modifier = Modifier.padding(0.dp, 0.dp),
        onExpandedChange = {
            expanded = !expanded
        },
    ) {
        OutlinedTextField(
            readOnly = true,
            enabled = enabled,
            value = camera,
            onValueChange = { },
            label = { Text(stringResource(id = R.string.camera_selector_label)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded
                )
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    // This value is used to assign to
                    // the DropDown the same width
                    mTextFieldSize = coordinates.size.toSize()
                }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
            },
            modifier = Modifier.width(with(LocalDensity.current) { mTextFieldSize.width.toDp() })
        ) {
            mCameras.forEach { selectionOption ->
                DropdownMenuItem(
                    text = { Text(text = selectionOption) },
                    onClick = {
                        onChangeCamera(selectionOption)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

// IR frequencies and patterns from Sebastian Setz
// http://sebastian.setz.name/arduino/my-libraries/multiCameraIrControl

// Other potential sources sources:
// https://github.com/krupski/sony_ir/blob/master/sony_ir.cpp (Sony, LGPL)

fun shutterNikon(irManager: ConsumerIrManager) {
    val pattern = intArrayOf(2000, 27830, 500, 1500, 500, 3500, 500)
    //val pattern = intArrayOf(2000, 27830, 400, 1580, 400, 3580, 400)
    irManager.transmit(38000, pattern)
}

fun shutterCanon(irManager: ConsumerIrManager) {
    val pattern = Array(32) { 11 }
    pattern[15] = 7341
    irManager.transmit(33000, pattern.toIntArray())
}

fun shutterPentax(irManager: ConsumerIrManager) {
    val pattern = Array(16) { 1000 }
    pattern[0] = 13000
    pattern[1] = 3000
    irManager.transmit(38000, pattern.toIntArray())
}

fun shutterOlympus(irManager: ConsumerIrManager) {
    // seq has length 32
    val seq: IntArray = intArrayOf(
        0,
        1,
        1,
        0,
        0,
        0,
        0,
        1,
        1,
        1,
        0,
        1,
        1,
        1,
        0,
        0,
        1,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        1,
        1,
        1,
        1,
        1,
        1,
        1
    )

    val pattern = Array(67) { 600 }
    pattern[0] = 8972
    pattern[1] = 4384
    pattern[2] = 624

    // set the variable durations for LOW
    for (i in seq.indices) {
        pattern[3 + 2 * i] = if (seq[i] == 1) 1600 else 488
    }

    irManager.transmit(40000, pattern.toIntArray())
}

fun shutterMinolta(irManager: ConsumerIrManager) {
    // seq has length 33
    val seq: IntArray = intArrayOf(
        0,
        0,
        1,
        0,
        1,
        1,
        0,
        0,
        0,
        1,
        0,
        1,
        0,
        0,
        1,
        1,
        1,
        0,
        0,
        0,
        0,
        0,
        1,
        0,
        1,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        1
    )

    val pattern = Array(68) { 456 }
    pattern[0] = 3750
    pattern[1] = 1890

    // set the variable durations for LOW
    for (i in seq.indices) {
        pattern[2 + 2 * i + 1] = if (seq[i] == 1) 1430 else 487
    }

    irManager.transmit(38000, pattern.toIntArray())
}

fun shutterSony(irManager: ConsumerIrManager) {
    // seq has length 20
    val seq: IntArray = intArrayOf(1, 1, 1, 0, 1, 1, 0, 0, 1, 0, 1, 1, 1, 0, 0, 0, 1, 1, 1, 1)

    var pattern = Array(43) { 650 }
    pattern[0] = 2320
    pattern[1] = 650

    // set the variable durations for HIGH
    for (i in seq.indices) {
        pattern[2 + 2 * i] = if (seq[i] == 1) 1175 else 575
    }

    pattern[42] = 10000

    pattern = pattern + pattern + pattern

    irManager.transmit(38000, pattern.toIntArray())
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {

    SimpleIRDSLRRemoteTheme {
        // A surface container using the 'background' color from the theme
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            MainColumn(handleExit = { })
        }
    }
}
