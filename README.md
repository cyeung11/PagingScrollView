# ReboundScrollView For Android

ReboundScrollView is a custom scroll view that offer a vertical ViewPager-like functionality.

![image](https://github.com/cyeung11/ReboundScrollView/blob/master/screenshot.gif)

Requirements
------------
- Android 4.0 or later (Minimum SDK level 15)

Install
------------

#### Project level gradle

```
allprojects {
    repositories {
        maven { url "https://jitpack.io" }
    }
}
```

#### App gradle
```
dependencies {
    implementation 'com.github.cyeung11:ReboundScrollView:1.0.1'
}
```

How to use
------------

Xml layout
```
  <com.jkjk.reboundscrollview.ReboundScrollView
      android:layout_width="match_parent"
      android:layout_height="match_parent">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical">

            <include layout="@layout/layout_1st_page" />

            <include layout="@layout/layout_2nd_page" />

            ...

        </LinearLayout>

  </com.jkjk.reboundscrollview.ReboundScrollView>
```


#### Page change factor
Determine the percentage of height needed to scroll to trigger page change.

from code
```
pageChangeThreshold = 50
```

from xml
```
app:page_change_threshold="50"
```


#### Listener
```
  addOnPageChangeListener(object : OnPageChangeListener {
        override fun onPageChange(view: ReboundScrollView, newPage: Int) {
            ...
        }
    })
```

