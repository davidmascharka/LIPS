# LIPS
LIPS (Learning-based Indoor Positioning System)

LIPS is a hybrid fingerprinting-based approach to indoor localization using the sensors available in smartphones. For information about the research done involving this application, see http://arxiv.org/abs/1505.06125 (pdf at http://arxiv.org/pdf/1505.06125v1.pdf).

# Machine Learning
The models in /assets were built using WEKA (Waikato Environment for Knowledge Analysis http://www.cs.waikato.ac.nz/ml/weka/). WEKA is also used in the code for predicting the user position in the TrackerActivity class. The WEKA website is a great resource for any issues there, as well as the WEKA mailing list. Documentation for the Java resources in WEKA is available at http://weka.sourceforge.net/doc.dev/ while instructions for using WEKA in Java code can be found at http://weka.wikispaces.com/Use+WEKA+in+your+Java+code and http://weka.wikispaces.com/Programmatic+Use. If there are problems, feel free to open an issue or send me an email.

# Building
Building the application and modifying it for usefulness in other buildings or areas of interest should be straightforward. Depending on the intended use of the application, much less code than this may be necessary. For help setting up an Android development environment, the documentation at http://developer.android.com/ is excellent. Regarding the code itself, comments are fairly liberal and variable names tend to be descriptive. Again, if you have any trouble, please open an issue here on Github or send me an email.

# Adding additional machine learning classifiers
Notice the RBFRegressor jar in libs. This is because RBFRegressor isn't by default installed with WEKA. To add RBFRegressor for use in code:
Open WEKA
Click the Tools tab, then Package Manager
Find and install the RBFNetwork package
The jar will be in wekafiles/packages/RBFNetwork/RBFNetwork.java

![GPLv3](http://www.gnu.org/graphics/gplv3-127x51.png)
