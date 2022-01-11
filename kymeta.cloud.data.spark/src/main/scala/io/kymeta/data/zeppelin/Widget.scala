package io.kymeta.data.zeppelin

object Widget {
  def datepicker(nextParagraph: String): Unit = {
    print(s"""%angular
<script type="text/javascript" src="//cdn.jsdelivr.net/bootstrap.daterangepicker/2/daterangepicker.js"></script>
<link rel="stylesheet" type="text/css" href="//cdn.jsdelivr.net/bootstrap.daterangepicker/2/daterangepicker.css" />

<div id="reportrange" style="background: #fff; cursor: pointer; padding: 5px 10px; border: 1px solid #ccc; width:250px;">
    <i class="glyphicon glyphicon-calendar fa fa-calendar"></i>&nbsp;
    <span></span> <b class="caret"></b>
</div>
<p>
<p>
<input id="start" ng-model="start" style="visibility: hidden;"></input>
<input id="end" ng-model="end" style="visibility: hidden;"></input>
<button type="submit" class="btn btn-primary pull-right" ng-click="z.angularBind('start',start,'$nextParagraph');z.angularBind('end',end,'$nextParagraph');z.runParagraph('$nextParagraph')"> Apply</button>
<script type="text/javascript">
$$(function() {
    var start = moment().subtract(29, 'days');
    var end = moment();
    function cb(start, end) {
        $$('#reportrange span').html(start.format('MMMM D, YYYY') + ' - ' + end.format('MMMM D, YYYY'));
        $$('#start').val(start.toISOString());
        $$('#start').trigger('input'); // Use for Chrome/Firefox/Edge
        $$('#start').trigger('change'); // Use for Chrome/Firefox/Edge + IE11
        $$('#end').val(end.toISOString());
        $$('#end').trigger('input'); // Use for Chrome/Firefox/Edge
        $$('#end').trigger('change'); // Use for Chrome/Firefox/Edge + IE11
    }

    $$('#reportrange').daterangepicker({
        startDate: start,
        endDate: end,
        ranges: {
           'Today': [moment(), moment()],
           'Yesterday': [moment().subtract(1, 'days'), moment().subtract(1, 'days')],
           'Last 7 Days': [moment().subtract(6, 'days'), moment()],
           'Last 30 Days': [moment().subtract(29, 'days'), moment()],
           'This Month': [moment().startOf('month'), moment().endOf('month')],
           'Last Month': [moment().subtract(1, 'month').startOf('month'), moment().subtract(1, 'month').endOf('month')]
        }
    }, cb);

    cb(start, end);
});
</script>""")
  }
}
