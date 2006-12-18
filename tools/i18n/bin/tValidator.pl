#!/usr/bin/perl

use strict;

################################################################################
# Copyright (c) JForum Team
# All rights reserved.
# 
# Redistribution and use in source and binary forms, 
# with or without modification, are permitted provided 
# that the following conditions are met:
# 
# 1) Redistributions of source code must retain the above 
# copyright notice, this list of conditions and the 
# following  disclaimer.
# 2)  Redistributions in binary form must reproduce the 
# above copyright notice, this list of conditions and 
# the following disclaimer in the documentation and/or 
# other materials provided with the distribution.
# 3) Neither the name of "Rafael Steil" nor 
# the names of its contributors may be used to endorse 
# or promote products derived from this software without 
# specific prior written permission.
# 
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT 
# HOLDERS AND CONTRIBUTORS "AS IS" AND ANY 
# EXPRESS OR IMPLIED WARRANTIES, INCLUDING, 
# BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR 
# PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL 
# THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE 
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES 
# (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
# SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, 
# OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER 
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER 
# IN CONTRACT, STRICT LIABILITY, OR TORT 
# (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN 
# ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF 
# ADVISED OF THE POSSIBILITY OF SUCH DAMAGE
# 
# The JForum Project
# http://www.jforum.net
################################################################################
# Author        : Jakob Vad Nielsen
# Created       : 18 dec. 2006
# Last modified : $Id: tValidator.pl,v 1.1.2.3 2006/12/18 13:48:19 lazee Exp $
################################################################################
# Perl script that validates a JForum 2.1.7 translation
#
# usage: perl tValidator.pl <path to jforum> <locale>
################################################################################

my $trans_dir = "WEB-INF/config/languages";
my $locale_prop_file = "WEB-INF/config/languages/locales.properties";

my $input_locale;
my $input_jforum_dir;

my %locales;
my $report;

my @icons = ("icon_pm.gif","icon_quote.gif","post.gif","reply_locked.gif",
            "icon_edit.gif","icon_profile.gif","msg_newpost.gif","reply.gif");
            
my $warnings = 0;
my $errors = 0;
my $missing_trans = 0;

# SYSTEM #######################################################################

$| = 1;
&controlInput();
&loadLocales();

if ($input_locale eq "en_US") {
    print "en_US is the reference language implementation, and can not be validated by this script!\n";
    exit(0);
}

if ($locales{$input_locale} eq "") {
    my $s = "The locale you have provided can not be found in locales.properties\n";
    $s .= "This means that you either have given an invalid locale, or that you\n";
    $s .= "should add your translation to this file.\n\n";
    $s .= "Please correct this, and then run the validator script again!";
    &addError($s);
    &printReport();
    exit(0);
}

my $agreement_file = $input_jforum_dir."templates/agreement/terms_".$input_locale.".txt";
if (! -f $agreement_file) {
    my $s = "Could not locate the file ".$agreement_file.". You should";
    $s .= " add this file, but it is not required.";
    &addWarning($s);
}

my $image_trans_dir = $input_jforum_dir."templates/default/images/".$input_locale;
if (! -d $image_trans_dir) {
    my $s = "Could not locate an image directory for this locale in: ".$image_trans_dir;
    $s .= ". This means that all image icons containing text will be presented in the";
    $s .= " default language, and not in the language you are validating.";
    $s .= "Please create this directory, and make a translation of all the files found in ";
    $s .= $input_jforum_dir."templates/default/images/en_US/";
    &addWarning($s);
} else {
    my $image_trans_css_file = $input_jforum_dir."templates/default/styles/".$input_locale.".css";
    if (! -f $image_trans_css_file) {
        my $s = $image_trans_css_file." is missing. Please create this file, and";
        $s .= " add similiar content found in other reference translations like en_US.css";
        &addError($s);
    }
    my $is;
    foreach $is (@icons) {
        if (! -f $image_trans_dir."/".$is) {
            &addError($image_trans_dir."/".$is." is missing!.");
        }
    }
}

my $en_trans_file = $input_jforum_dir."WEB-INF/config/languages/en_US.properties";
my $locale_trans_file = $input_jforum_dir."WEB-INF/config/languages/".$input_locale.".properties";

if (! -f $en_trans_file) {
    &addError("Can not locate the en_US translation file ".$en_trans_file);
} else {
    if (! -f $locale_trans_file) {
        &addError("Can not find a translation file for the locale specified. Please create ".$locale_trans_file.", and add all translations based on the text in ".$en_trans_file);
    } else {
        my %en_trans = &loadTranslationFile($en_trans_file);
        my %locale_trans = &loadTranslationFile($locale_trans_file);
        my $k;
        foreach $k (keys %en_trans) {
            if ($locale_trans{$k} eq "") {
                &addMissingTrans($k." is missing in ".$input_locale.".properties");
            }
        }
    }
}

&printReport($report);

# SUBS #########################################################################

sub controlInput {
    my $out_usage = "Usage: tValidator.pl <jforum dir> <locale>\n\nExample: tValidator.pl /home/jforum en_US";
    if (@ARGV != 2) {
        print $out_usage;
        exit(0);
    } else {
        $input_jforum_dir = $ARGV[0];
        if ($input_jforum_dir !~ /\/$/) {
            $input_jforum_dir .= "/";
        }
        $input_locale = $ARGV[1];
        
        if (! -d $input_jforum_dir) {
            print $input_jforum_dir." is not a valid directory\n";
            exit(0);
        }
    }
    
}

sub loadLocales {
    open(FILE, $input_jforum_dir.$locale_prop_file) || die "ERROR: Couldn't open locale.properties";
    my @file = <FILE>;
    close(FILE);
    my $l;
    foreach $l (@file) {
        $l =~ &trim($l);
        if ($l ne "") {
            my ($k, $v) = split('=', $l, 2);
            $k = &trim($k);
            $v = &trim($v);
            $locales{$k} = $v
        }
    }
}

sub loadTranslationFile {
    my %l;
    my ( $f ) = @_;
    open(FILE, $f) || die "Could not open ".$f." for reading";
    my @file = <FILE>;
    close(FILE);
    my $s;
    foreach $s (@file) {
        my @a = split('=', $s, 2);
        my $k = &trim($a[0]);
        my $v = &trim($a[1]);
        if ($k =~ /^\#/ || $k eq "") {
            # skip
            # print "Skipping ".$k."\n";
        } else {
            $l{$k} = $v;
        }
    }
    return %l;
}

sub trim {
    my ( $s ) = @_;
    $s =~ s/^\s*//g;
    $s =~ s/\s*$//g;
    return $s;
}

sub printReport {
   print "\nREPORT:\n";
   print "-----------------------------------------\n";
   print $report;
   if ($warnings + $errors + $missing_trans == 0) {
        print "Success!!! This seems to be a complete translation :) \n";
   }
   print "-----------------------------------------\n";
   print "Warnings              : ".$warnings."\n";
   print "Errors                : ".$errors."\n";   
   print "Missing translations  : ".$missing_trans."\n";   
   print "-----------------------------------------\n";
}

sub addWarning {
   my ( $s ) = @_;
   $warnings++;
   $report .= "WARNING [".$warnings."]: ".$s."\n";
 
}

sub addError {
   my ( $s ) = @_;
   $errors++;
   $report .= "ERROR [".$errors."]: ".$s."\n";
}

sub addMissingTrans {
   my ( $s ) = @_;
   $missing_trans++;
   $report .= "MISSING TRANSLATION [".$missing_trans."]: ".$s."\n";
}
