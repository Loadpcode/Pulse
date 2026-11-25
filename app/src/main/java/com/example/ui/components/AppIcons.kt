package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Compact inline Material vector icons replacing the ~20 MB material-icons-extended dependency.
 */
object AppIcons {
    val Apps: ImageVector by lazy {
        icon(
            "Apps",
            "M4,8h4V4H4v4zM10,20h4v-4h-4v4zM4,20h4v-4H4v4zM4,14h4v-4H4v4zM10,14h4v-4h-4v4zM16,4v4h4V4h-4zM10,8h4V4h-4v4zM16,14h4v-4h-4v4zM16,20h4v-4h-4v4z"
        )
    }

    val Brush: ImageVector by lazy {
        icon(
            "Brush",
            "M7,14c-1.66,0 -3,1.34 -3,3 0,1.31 -1.16,2 -2,2 0.92,1.22 2.49,2 4,2 2.21,0 4,-1.79 4,-4 0,-1.66 -1.34,-3 -3,-3zM20.71,4.63l-1.34,-1.34c-0.39,-0.39 -1.02,-0.39 -1.41,0L9,12.25 11.75,15l8.96,-8.96c0.39,-0.39 0.39,-1.02 0,-1.41z"
        )
    }

    val ChevronRight: ImageVector by lazy {
        icon(
            "ChevronRight",
            "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z"
        )
    }

    val Code: ImageVector by lazy {
        icon(
            "Code",
            "M9.4,16.6L4.8,12l4.6,-4.6L8,6l-6,6 6,6 1.4,-1.4zM14.6,16.6l4.6,-4.6 -4.6,-4.6L16,6l6,6 -6,6 -1.4,-1.4z"
        )
    }

    val Colorize: ImageVector by lazy {
        icon(
            "Colorize",
            "M20.71,5.63l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-3.12,3.12 -1.93,-1.91 -1.41,1.41 1.42,1.42L3,16.25V21h4.75l8.92,-8.92 1.42,1.42 1.41,-1.41 -1.92,-1.92 3.12,-3.12c0.4,-0.4 0.4,-1.03 0.01,-1.42zM6.92,19L5,17.08l8.06,-8.06 1.92,1.92L6.92,19z"
        )
    }

    val ContentCopy: ImageVector by lazy {
        icon(
            "ContentCopy",
            "M16,1H4c-1.1,0 -2,0.9 -2,2v14h2V3h12V1zM19,5H8c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2zM19,21H8V7h11v14z"
        )
    }

    val ContentPaste: ImageVector by lazy {
        icon(
            "ContentPaste",
            "M19,2h-4.18C14.4,0.84 13.3,0 12,0c-1.3,0 -2.4,0.84 -2.82,2H5c-1.1,0 -2,0.9 -2,2v16c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM12,2c0.55,0 1,0.45 1,1s-0.45,1 -1,1 -1,-0.45 -1,-1 0.45,-1 1,-1zM19,20H5V4h2v3h10V4h2v16z"
        )
    }

    val DeleteSweep: ImageVector by lazy {
        icon(
            "DeleteSweep",
            "M15,16h4v2h-4zM15,8h7v2h-7zM15,12h6v2h-6zM3,18c0,1.1 0.9,2 2,2h6c1.1,0 2,-0.9 2,-2V8H3v10zM14,5h-3l-1,-1H6L5,5H2v2h12z"
        )
    }

    val EditOutlined: ImageVector by lazy {
        icon(
            "EditOutlined",
            "M14.06,9.02l0.92,0.92L5.92,19H5v-0.92l9.06,-9.06M17.66,3c-0.25,0 -0.51,0.1 -0.7,0.29l-1.83,1.83 3.75,3.75 1.83,-1.83c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.2,-0.2 -0.45,-0.29 -0.71,-0.29zM14.06,6.19L3,17.25V21h3.75L17.81,9.94l-3.75,-3.75z"
        )
    }

    val FlashOff: ImageVector by lazy {
        icon(
            "FlashOff",
            "M17,10h-3.61l2.28,2.28zM17,2H7v1.61l6.13,6.13zM3.41,2.86L2,4.27l5,5V13h3v9l3.58,-6.15L17.73,20l1.41,-1.41z"
        )
    }

    val FlashOn: ImageVector by lazy {
        icon(
            "FlashOn",
            "M7,2v11h3v9l7,-12h-4l4,-8z"
        )
    }

    val NetworkCheck: ImageVector by lazy {
        icon(
            "NetworkCheck",
            "M15.9,5c-0.17,0 -0.32,0.09 -0.41,0.23l-0.07,0.15 -5.18,11.65c-0.16,0.29 -0.26,0.61 -0.26,0.96 0,1.11 0.9,2.01 2.01,2.01 0.96,0 1.77,-0.68 1.96,-1.59l0.01,-0.03L16.4,5.5c0,-0.28 -0.22,-0.5 -0.5,-0.5zM1,9l2,2c2.88,-2.88 6.79,-4.08 10.53,-3.62l1.19,-2.68C9.89,3.84 4.74,5.27 1,9zM21,11l2,-2c-1.64,-1.64 -3.55,-2.82 -5.59,-3.57l-0.53,2.82c1.5,0.62 2.9,1.53 4.12,2.75zM17,15l2,-2c-0.8,-0.8 -1.7,-1.42 -2.66,-1.89l-0.55,2.92c0.42,0.27 0.83,0.59 1.21,0.97zM5,13l2,2c1.13,-1.13 2.56,-1.79 4.03,-2l1.28,-2.88c-2.63,-0.08 -5.3,0.87 -7.31,2.88z"
        )
    }

    val NoPhotography: ImageVector by lazy {
        icon(
            "NoPhotography",
            "M10.94,8.12L7.48,4.66 9,3h6l1.83,2H20c1.1,0 2,0.9 2,2v12c0,0.05 -0.01,0.1 -0.02,0.16l-5.1,-5.1c0.08,-0.35 0.12,-0.7 0.12,-1.06 0,-2.76 -2.24,-5 -5,-5 -0.36,0 -0.71,0.04 -1.06,0.12zM20.49,23.31L18.17,21H4c-1.1,0 -2,-0.9 -2,-2V7c0,-0.59 0.27,-1.12 0.68,-1.49L0.69,3.51 2.1,2.1l19.8,19.8 -1.41,1.41zM14.49,17.32l-1.5,-1.5C12.67,15.94 12.35,16 12,16c-1.66,0 -3,-1.34 -3,-3 0,-0.35 0.06,-0.67 0.17,-0.98l-1.5,-1.5C7.25,11.24 7,12.09 7,13c0,2.76 2.24,5 5,5 0.91,0 1.76,-0.25 2.49,-0.68z"
        )
    }

    val Palette: ImageVector by lazy {
        icon(
            "Palette",
            "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9c0.83,0 1.5,-0.67 1.5,-1.5 0,-0.39 -0.15,-0.74 -0.39,-1.01 -0.23,-0.26 -0.38,-0.61 -0.38,-0.99 0,-0.83 0.67,-1.5 1.5,-1.5H16c2.76,0 5,-2.24 5,-5 0,-4.42 -4.03,-8 -9,-8zM6.5,12c-0.83,0 -1.5,-0.67 -1.5,-1.5S5.67,9 6.5,9 8,9.67 8,10.5 7.33,12 6.5,12zM9.5,8C8.67,8 8,7.33 8,6.5S8.67,5 9.5,5s1.5,0.67 1.5,1.5S10.33,8 9.5,8zM14.5,8c-0.83,0 -1.5,-0.67 -1.5,-1.5S13.67,5 14.5,5s1.5,0.67 1.5,1.5S15.33,8 14.5,8zM17.5,12c-0.83,0 -1.5,-0.67 -1.5,-1.5S16.67,9 17.5,9s1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5z"
        )
    }

    val PowerSettingsNew: ImageVector by lazy {
        icon(
            "PowerSettingsNew",
            "M13,3h-2v10h2V3zM17.83,5.17l-1.42,1.42C17.99,7.86 19,9.81 19,12c0,3.87 -3.13,7 -7,7s-7,-3.13 -7,-7c0,-2.19 1.01,-4.14 2.58,-5.42L6.17,5.17C4.23,6.82 3,9.26 3,12c0,4.97 4.03,9 9,9s9,-4.03 9,-9c0,-2.74 -1.23,-5.18 -3.17,-6.83z"
        )
    }

    val QrCodeScanner: ImageVector by lazy {
        icon(
            "QrCodeScanner",
            "M9.5,6.5v3h-3v-3h3M11,5H5v6h6V5zM9.5,14.5v3h-3v-3h3M11,13H5v6h6V13zM17.5,6.5v3h-3v-3h3M19,5h-6v6h6V5zM13,13h1.5v1.5H13V13zM14.5,14.5H16V16h-1.5V14.5zM16,13h1.5v1.5H16V13zM13,16h1.5v1.5H13V16zM14.5,17.5H16V19h-1.5V17.5zM16,16h1.5v1.5H16V16zM17.5,14.5H19V16h-1.5V14.5zM17.5,17.5H19V19h-1.5V17.5zM22,7h-2V4h-3V2h5V7zM22,22v-5h-2v3h-3v2H22zM2,22h5v-2H4v-3H2V22zM2,2v5h2V4h3V2H2z"
        )
    }

    val RestartAlt: ImageVector by lazy {
        icon(
            "RestartAlt",
            "M12,5V2L8,6l4,4V7c3.31,0 6,2.69 6,6 0,2.97 -2.17,5.43 -5,5.91v2.02c3.95,-0.49 7,-3.85 7,-7.93 0,-4.42 -3.58,-8 -8,-8zM6,13c0,-1.65 0.67,-3.15 1.76,-4.24L6.34,7.34C4.9,8.79 4,10.79 4,13c0,4.08 3.05,7.44 7,7.93v-2.02c-2.83,-0.48 -5,-2.94 -5,-5.91z"
        )
    }

    val Share: ImageVector by lazy {
        icon(
            "Share",
            "M18,16.08c-0.76,0 -1.44,0.3 -1.96,0.77L8.91,12.7c0.05,-0.23 0.09,-0.46 0.09,-0.7s-0.04,-0.47 -0.09,-0.7l7.05,-4.11c0.54,0.5 1.25,0.81 2.04,0.81 1.66,0 3,-1.34 3,-3s-1.34,-3 -3,-3 -3,1.34 -3,3c0,0.24 0.04,0.47 0.09,0.7L8.04,9.81C7.5,9.31 6.79,9 6,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3c0.79,0 1.5,-0.31 2.04,-0.81l7.12,4.16c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.61 1.31,2.92 2.92,2.92 1.61,0 2.92,-1.31 2.92,-2.92s-1.31,-2.92 -2.92,-2.92z"
        )
    }

    val Terminal: ImageVector by lazy {
        icon(
            "Terminal",
            "M20,4H4c-1.11,0 -2,0.9 -2,2v12c0,1.1 0.89,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2zM20,18H4V8h16v10zM18,17h-6v-2h6v2zM7.5,17l-1.41,-1.41L8.67,13l-2.59,-2.59L7.5,9l4,4 -4,4z"
        )
    }

    val Tune: ImageVector by lazy {
        icon(
            "Tune",
            "M3,17v2h6v-2H3zM3,5v2h10V5H3zM13,21v-2h8v-2h-8v-2h-2v6h2zM7,9v2H3v2h4v2h2V9H7zM21,13v-2H11v2h10zM15,9h2V7h4V5h-4V3h-2v6z"
        )
    }

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).addPath(
            pathData = addPathNodes(pathData),
            fill = SolidColor(Color.Black)
        ).build()
}
