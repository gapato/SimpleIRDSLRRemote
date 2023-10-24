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
import android.os.Handler
import android.os.Looper
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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import eu.oknaj.simpleirdslrremote.ui.theme.SimpleIRDSLRRemoteTheme

val mCameras = listOf("Nikon",
    "Canon" + R.string.untested_suffix,
    "Minolta" + R.string.untested_suffix,
    "Olympus" + R.string.untested_suffix,
    "Pentax" + R.string.untested_suffix,
    "Sony" + R.string.untested_suffix)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SimpleIRDSLRRemoteTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
    var delay by remember { mutableStateOf(false) }
    var shuttering by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Greeting(hasIr)
        if (hasIr) {
            CameraSelector(camera, onChangeCamera = { camera = it})
            ShutterModeSwitch(delay, onChangeMode = { delay = it })
            ShutterButton(irManager, camera, delay, shuttering, onShutterOpen = { shuttering = true }, onShutterClose = { shuttering = false })
        }
        Spacer(Modifier.height(40.dp))
        ExitButton(handleExitClick = handleExit)
    }
}

@Composable
fun Greeting(hasIr: Boolean, modifier: Modifier = Modifier) {
    var headingText = stringResource(id = R.string.no_ir_found)

    if (hasIr) {
        headingText = stringResource(id = R.string.ir_found)
    }

    Text(
            text = headingText,
            modifier = modifier.padding(10.dp)
    )
}

@Composable
fun ShutterModeSwitch(delay: Boolean, onChangeMode: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp, 0.dp)) {
        Text(text = stringResource(id = R.string.delay_label))
        Switch(
            checked = delay,
            onCheckedChange = { it:Boolean -> onChangeMode(it) }
        )
    }
}

@Composable
fun ShutterButton(irManager: ConsumerIrManager, camera: String, delay:Boolean,
                  shuttering: Boolean, onShutterOpen: () -> Unit, onShutterClose: () -> Unit) {
    Button(
        onClick = { shutterAction(irManager = irManager, camera, delay, onShutterOpen, onShutterClose) },
        enabled = !shuttering,
        modifier = Modifier
            .fillMaxWidth(0.9F)
            .fillMaxHeight(0.8F),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(text = stringResource(id = R.string.shutter_label))
    }
}

fun shutterAction(irManager: ConsumerIrManager, camera: String, delay:Boolean,
                  onShutterOpen: () -> Unit, onShutterClose: () -> Unit) {
    val idx = mCameras.indexOf(camera)

    val shutterFunctions = arrayOf(::shutterNikon, ::shutterCanon, ::shutterMinolta, ::shutterOlympus, ::shutterPentax, ::shutterSony)

    if (idx >= 0) {
        if (delay) {
            onShutterOpen()
            Handler(Looper.getMainLooper()).postDelayed({
                shutterFunctions[idx](irManager)
                onShutterClose()
            }, 2000)
        } else {
            onShutterOpen()
            shutterFunctions[idx](irManager)
            onShutterClose()
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
fun CameraSelector(camera:String, onChangeCamera: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var mTextFieldSize by remember { mutableStateOf(Size.Zero)}

// We want to react on tap/press on TextField to show menu
    ExposedDropdownMenuBox(
        expanded = expanded,
        modifier = Modifier.padding(20.dp, 0.dp),
        onExpandedChange = {
            expanded = !expanded
        },
    ) {
        OutlinedTextField(
            readOnly = true,
            value = camera,
            onValueChange = { },
            label = { Text(stringResource(id = R.string.camera_selector_label)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded
                )
            },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
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
            modifier = Modifier.width(with(LocalDensity.current){mTextFieldSize.width.toDp()})
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

fun shutterNikon(irManager: ConsumerIrManager) {
    //val pattern = intArrayOf(2000, 27830, 500, 1500, 500, 3500, 500)
    val pattern = intArrayOf(2000, 27830, 390, 1580, 410, 3580, 400)
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
    val seq: IntArray = intArrayOf(0,1,1,0,0,0,0,1,1,1,0,1,1,1,0,0,1,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1)

    val pattern = Array(67) { 600 }
    pattern[0] = 8972
    pattern[1] = 4384
    pattern[2] = 624

    // set the variable durations for LOW
    for (i in seq.indices) {
        pattern[3 + 2*i] = if (seq[i] == 1) 1600 else 488
    }

    irManager.transmit(40000, pattern.toIntArray())
}

fun shutterMinolta(irManager: ConsumerIrManager) {
    // seq has length 33
    val seq: IntArray = intArrayOf(0,0,1,0,1,1,0,0,0,1,0,1,0,0,1,1,1,0,0,0,0,0,1,0,1,0,0,0,0,0,0,0,1)

    val pattern = Array(68) { 456 }
    pattern[0] = 3750
    pattern[1] = 1890

    // set the variable durations for LOW
    for (i in seq.indices) {
        pattern[2 + 2*i + 1] = if (seq[i] == 1) 1430 else 487
    }

    irManager.transmit(38000, pattern.toIntArray())
}

fun shutterSony(irManager: ConsumerIrManager) {
    // seq has length 20
    val seq: IntArray = intArrayOf(1,1,1,0,1,1,0,0,1,0,1,1,1,0,0,0,1,1,1,1)

    var pattern = Array(43) { 650 }
    pattern[0] = 2320
    pattern[1] = 650

    // set the variable durations for HIGH
    for (i in seq.indices) {
        pattern[2 + 2*i] = if (seq[i] == 1) 1175 else 575
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
